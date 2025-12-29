import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

class WebSocketService {
    client = null
    subscriptions = new Map()
    handlers = new Map()
    isConnected = false
    reconnectAttempts = 0
    maxReconnectAttempts = 5
    baseReconnectDelay = 1000
    reconnectTimer = null
    onConnectionChange = null
    currentToken = null
    currentRoomCode = null
    currentCallbacks = {}
    isReconnecting = false
    tokenRefreshCallback = null

    connect(token, callbacks = {}) {
        if (this.client?.connected) {
            console.log('[WS] 已连接')
            return
        }
        this.currentToken = token
        this.currentCallbacks = callbacks
        this.tokenRefreshCallback = callbacks.onTokenRefresh || null
        this.reconnectAttempts = 0
        this.isReconnecting = false
        this._createClient(token, callbacks)
    }

    _createClient(token, callbacks) {
        if (this.client) {
            try { this.client.deactivate() } catch (e) { /* ignore */ }
        }

        this.client = new Client({
            webSocketFactory: () => new SockJS('/ws'),
            connectHeaders: { Authorization: `Bearer ${token}` },
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            reconnectDelay: 0,

            onConnect: () => {
                console.log('[WS] 连接成功')
                this.isConnected = true
                this.reconnectAttempts = 0
                this.isReconnecting = false
                this.resubscribeAll()
                this._notifyConnectionChange(true)
                if (this.currentRoomCode) this._recoverGameState()
                callbacks.onConnect?.()
            },

            onDisconnect: (frame) => {
                console.log('[WS] 连接断开', frame)
                this.isConnected = false
                this._notifyConnectionChange(false)
                this._attemptReconnect(this.currentCallbacks)
                callbacks.onDisconnect?.(frame)
            },

            onStompError: (frame) => {
                console.error('[WS] STOMP错误:', frame)
                if (frame.headers?.message?.includes('401') || 
                    frame.headers?.message?.includes('Unauthorized')) {
                    this._handleAuthError(callbacks)
                }
                callbacks.onStompError?.(frame)
            },

            onWebSocketError: (error) => {
                console.error('[WS] 连接错误:', error)
                this.isConnected = false
                this._notifyConnectionChange(false)
                callbacks.onWebSocketError?.(error)
            },

            onWebSocketClose: () => {
                this.isConnected = false
                this._notifyConnectionChange(false)
            }
        })

        this.client.activate()
    }

    async _handleAuthError(callbacks) {
        console.log('[WS] 认证失败，尝试刷新Token')
        if (this.tokenRefreshCallback) {
            try {
                const newToken = await this.tokenRefreshCallback()
                if (newToken) {
                    this.currentToken = newToken
                    console.log('[WS] Token刷新成功，正在重连')
                    this._createClient(newToken, callbacks)
                    return
                }
            } catch (e) {
                console.error('[WS] Token刷新失败:', e)
            }
        }
        callbacks.onAuthError?.()
    }

    _attemptReconnect(callbacks) {
        if (this.isReconnecting) {
            console.log('[WS] 正在重连中，跳过')
            return
        }
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('[WS] 已达最大重连次数')
            this.isReconnecting = false
            callbacks.onReconnectFailed?.()
            return
        }
        if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
        
        this.isReconnecting = true
        this.reconnectAttempts++
        const delay = this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts - 1)
        console.log(`[WS] ${delay/1000}秒后重连 (第${this.reconnectAttempts}次)`)
        callbacks.onReconnecting?.(this.reconnectAttempts, delay)
        
        this.reconnectTimer = setTimeout(async () => {
            if (this.currentToken) {
                if (this.tokenRefreshCallback && this.reconnectAttempts > 1) {
                    try {
                        const newToken = await this.tokenRefreshCallback()
                        if (newToken) {
                            this.currentToken = newToken
                            console.log('[WS] 重连前Token刷新成功')
                        }
                    } catch (e) {
                        console.warn('[WS] Token刷新失败，使用现有Token')
                    }
                }
                this._createClient(this.currentToken, callbacks)
            }
        }, delay)
    }

    _notifyConnectionChange(connected) {
        if (this.onConnectionChange) this.onConnectionChange(connected)
    }

    async _recoverGameState() {
        if (!this.currentRoomCode || !this.client?.connected) return
        console.log('[WS] 恢复游戏状态:', this.currentRoomCode)
        try {
            this.send(`/app/room/${this.currentRoomCode}/recover`, { timestamp: Date.now() })
        } catch (error) {
            console.error('[WS] 恢复失败:', error)
        }
    }

    setCurrentRoom(roomCode) { this.currentRoomCode = roomCode }
    clearCurrentRoom() { this.currentRoomCode = null }
    setConnectionChangeCallback(callback) { this.onConnectionChange = callback }

    subscribe(topic, handler) {
        this.handlers.set(topic, handler)
        if (this.client?.connected) {
            try {
                const existingSub = this.subscriptions.get(topic)
                if (existingSub) existingSub.unsubscribe()
                const subscription = this.client.subscribe(topic, handler)
                this.subscriptions.set(topic, subscription)
            } catch (error) {
                console.error('[WS] 订阅失败:', topic, error)
            }
        }
    }

    unsubscribe(topic) {
        const subscription = this.subscriptions.get(topic)
        if (subscription) {
            try { subscription.unsubscribe() } catch (e) { /* ignore */ }
            this.subscriptions.delete(topic)
        }
        this.handlers.delete(topic)
    }

    send(destination, body) {
        if (!this.client?.connected) {
            console.error('[WS] 发送失败: 未连接', destination)
            return false
        }
        try {
            this.client.publish({ destination, body: JSON.stringify(body) })
            return true
        } catch (error) {
            console.error('[WS] 发送错误:', destination, error)
            return false
        }
    }

    async sendWithRetry(destination, body, maxRetries = 3) {
        for (let i = 0; i < maxRetries; i++) {
            if (this.send(destination, body)) return true
            await new Promise(resolve => setTimeout(resolve, 500 * (i + 1)))
            if (!this.client?.connected) await new Promise(resolve => setTimeout(resolve, 1000))
        }
        console.error('[WS] 重试后仍发送失败:', destination)
        return false
    }

    resubscribeAll() {
        this.subscriptions.clear()
        for (const [topic, handler] of this.handlers.entries()) {
            try {
                const subscription = this.client.subscribe(topic, handler)
                this.subscriptions.set(topic, subscription)
            } catch (error) {
                console.error('[WS] 重新订阅失败:', topic, error)
            }
        }
    }

    disconnect() {
        this.isReconnecting = false
        if (this.reconnectTimer) {
            clearTimeout(this.reconnectTimer)
            this.reconnectTimer = null
        }
        if (this.client) {
            try { this.client.deactivate() } catch (e) { /* ignore */ }
            this.client = null
        }
        this.subscriptions.clear()
        this.handlers.clear()
        this.isConnected = false
        this.currentToken = null
        this.currentRoomCode = null
        this.currentCallbacks = {}
        this.reconnectAttempts = 0
    }

    reconnect() {
        if (this.currentToken) {
            this.reconnectAttempts = 0
            this.isReconnecting = false
            this._createClient(this.currentToken, this.currentCallbacks)
        }
    }

    isSubscribed(topic) { return this.subscriptions.has(topic) }
    getSubscribedTopics() { return Array.from(this.subscriptions.keys()) }
    get connected() { return this.client?.connected || false }
    get currentReconnectAttempts() { return this.reconnectAttempts }

    getConnectionInfo() {
        return {
            connected: this.connected,
            reconnectAttempts: this.reconnectAttempts,
            subscribedTopics: this.getSubscribedTopics(),
            currentRoom: this.currentRoomCode,
            isReconnecting: this.isReconnecting
        }
    }
}

export const wsService = new WebSocketService()
export { WebSocketService }
