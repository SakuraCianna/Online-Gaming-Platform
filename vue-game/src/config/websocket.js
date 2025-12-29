import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

class WebSocketService {
    client = null
    subscriptions = new Map()
    handlers = new Map()
    isConnected = false
    reconnectAttempts = 0
    maxReconnectAttempts = 3
    baseReconnectDelay = 1000
    reconnectTimer = null
    onConnectionChange = null
    currentToken = null
    currentRoomCode = null
    currentCallbacks = {}
    isReconnecting = false // 防止重复重连

    connect(token, callbacks = {}) {
        if (this.client?.connected) {
            console.log('[WS] Already connected')
            return
        }
        this.currentToken = token
        this.currentCallbacks = callbacks
        this.reconnectAttempts = 0
        this.isReconnecting = false
        this._createClient(token, callbacks)
    }

    _createClient(token, callbacks) {
        if (this.client) {
            try { this.client.deactivate() } catch (e) { /* ignore */ }
        }

        const socketFactory = () => new SockJS(`/ws?token=${token}`)

        this.client = new Client({
            webSocketFactory: socketFactory,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            reconnectDelay: 0,

            onConnect: () => {
                console.log('[WS] Connected')
                this.isConnected = true
                this.reconnectAttempts = 0
                this.isReconnecting = false
                this.resubscribeAll()
                this._notifyConnectionChange(true)
                if (this.currentRoomCode) this._recoverGameState()
                callbacks.onConnect?.()
            },

            onDisconnect: (frame) => {
                console.log('[WS] Disconnected', frame)
                this.isConnected = false
                this._notifyConnectionChange(false)
                this._attemptReconnect(this.currentCallbacks)
                callbacks.onDisconnect?.(frame)
            },

            onStompError: (frame) => {
                console.error('[WS] STOMP Error:', frame)
                callbacks.onStompError?.(frame)
            },

            onWebSocketError: (error) => {
                console.error('[WS] WebSocket Error:', error)
                this.isConnected = false
                this._notifyConnectionChange(false)
                // 不在这里重连，让onDisconnect统一处理
                callbacks.onWebSocketError?.(error)
            },

            onWebSocketClose: () => {
                this.isConnected = false
                this._notifyConnectionChange(false)
            }
        })

        this.client.activate()
    }


    _attemptReconnect(callbacks) {
        // 防止重复重连
        if (this.isReconnecting) {
            console.log('[WS] Already reconnecting, skip')
            return
        }
        if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            console.error('[WS] Max reconnect attempts reached')
            this.isReconnecting = false
            callbacks.onReconnectFailed?.()
            return
        }
        if (this.reconnectTimer) clearTimeout(this.reconnectTimer)
        
        this.isReconnecting = true
        this.reconnectAttempts++
        const delay = this.baseReconnectDelay * Math.pow(2, this.reconnectAttempts - 1)
        console.log(`[WS] Reconnecting in ${delay/1000}s (attempt ${this.reconnectAttempts})`)
        callbacks.onReconnecting?.(this.reconnectAttempts, delay)
        this.reconnectTimer = setTimeout(() => {
            if (this.currentToken) this._createClient(this.currentToken, callbacks)
        }, delay)
    }

    _notifyConnectionChange(connected) {
        if (this.onConnectionChange) this.onConnectionChange(connected)
    }

    async _recoverGameState() {
        if (!this.currentRoomCode || !this.client?.connected) return
        console.log('[WS] Recovering game state:', this.currentRoomCode)
        try {
            this.send(`/app/room/${this.currentRoomCode}/recover`, { timestamp: Date.now() })
        } catch (error) {
            console.error('[WS] Recovery failed:', error)
        }
    }

    setCurrentRoom(roomCode) {
        this.currentRoomCode = roomCode
    }

    clearCurrentRoom() {
        this.currentRoomCode = null
    }

    setConnectionChangeCallback(callback) {
        this.onConnectionChange = callback
    }

    subscribe(topic, handler) {
        this.handlers.set(topic, handler)
        if (this.client?.connected) {
            try {
                const existingSub = this.subscriptions.get(topic)
                if (existingSub) existingSub.unsubscribe()
                const subscription = this.client.subscribe(topic, handler)
                this.subscriptions.set(topic, subscription)
            } catch (error) {
                console.error('[WS] Subscribe failed:', topic, error)
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
            console.error('[WS] Send failed: not connected', destination)
            return false
        }
        try {
            this.client.publish({ destination, body: JSON.stringify(body) })
            return true
        } catch (error) {
            console.error('[WS] Send error:', destination, error)
            return false
        }
    }

    async sendWithRetry(destination, body, maxRetries = 3) {
        for (let i = 0; i < maxRetries; i++) {
            if (this.send(destination, body)) return true
            await new Promise(resolve => setTimeout(resolve, 500 * (i + 1)))
            if (!this.client?.connected) await new Promise(resolve => setTimeout(resolve, 1000))
        }
        console.error('[WS] Send failed after retries:', destination)
        return false
    }

    resubscribeAll() {
        this.subscriptions.clear()
        for (const [topic, handler] of this.handlers.entries()) {
            try {
                const subscription = this.client.subscribe(topic, handler)
                this.subscriptions.set(topic, subscription)
            } catch (error) {
                console.error('[WS] Resubscribe failed:', topic, error)
            }
        }
    }

    disconnect() {
        // 停止重连
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
