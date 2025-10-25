import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

/**
 * WebSocket 服务类
 * 统一管理 STOMP over SockJS 连接和订阅
 */
class WebSocketService {
    client = null
    subscriptions = new Map()  // topic => subscription 对象
    handlers = new Map()       // topic => handler 函数
    isConnected = false

    /**
     * 建立连接
     * @param {string} token - JWT Token
     * @param {object} callbacks - 回调函数 { onConnect, onStompError, onWebSocketError, onDisconnect }
     */
    connect(token, callbacks = {}) {
        if (this.client) {
            return
        }

        const socketFactory = () => new SockJS(`/ws?token=${token}`)

        this.client = new Client({
            webSocketFactory: socketFactory,
            reconnectDelay: 5000,

            onConnect: () => {
                this.isConnected = true
                this.resubscribeAll()
                callbacks.onConnect?.()
            },

            onDisconnect: () => {
                this.isConnected = false
                callbacks.onDisconnect?.()
            },

            onStompError: (frame) => {
                callbacks.onStompError?.(frame)
            },

            onWebSocketError: (error) => {
                callbacks.onWebSocketError?.(error)
            }
        })

        this.client.activate()
    }

    /**
     * 订阅主题
     * @param {string} topic - 订阅主题，如 '/topic/chat/123'
     * @param {function} handler - 消息处理函数
     */
    subscribe(topic, handler) {
        // 保存 handler，用于重连后重新订阅
        this.handlers.set(topic, handler)

        // 如果已连接，立即订阅
        if (this.client?.connected) {
            try {
                // 如果已有订阅，先取消
                const existingSub = this.subscriptions.get(topic)
                if (existingSub) {
                    existingSub.unsubscribe()
                }

                const subscription = this.client.subscribe(topic, handler)
                this.subscriptions.set(topic, subscription)
            } catch (error) {
                console.error(error)
                // 订阅失败
            }
        }
    }

    /**
     * 取消订阅
     * @param {string} topic - 主题
     */
    unsubscribe(topic) {
        const subscription = this.subscriptions.get(topic)
        if (subscription) {
            subscription.unsubscribe()
            this.subscriptions.delete(topic)
        }

        this.handlers.delete(topic)
    }

    /**
     * 发送消息
     * @param {string} destination - 目标地址，如 '/app/chat'
     * @param {object} body - 消息体
     */
    send(destination, body) {
        if (!this.client?.connected) {
            return false
        }

        try {
            this.client.publish({
                destination,
                body: JSON.stringify(body)
            })
            return true
        } catch (error) {
            console.error(error)
            return false
        }
    }

    /**
     * 重新订阅所有主题（用于重连后）
     */
    resubscribeAll() {
        this.subscriptions.clear()

        for (const [topic, handler] of this.handlers.entries()) {
            try {
                const subscription = this.client.subscribe(topic, handler)
                this.subscriptions.set(topic, subscription)
            } catch (error) {
                console.error(error)
                // 重新订阅失败
            }
        }
    }

    /**
     * 断开连接
     */
    disconnect() {
        if (this.client) {
            this.client.deactivate()
            this.client = null
        }

        this.subscriptions.clear()
        this.handlers.clear()
        this.isConnected = false
    }

    /**
     * 检查是否已订阅某主题
     */
    isSubscribed(topic) {
        return this.subscriptions.has(topic)
    }

    /**
     * 获取所有已订阅的主题
     */
    getSubscribedTopics() {
        return Array.from(this.subscriptions.keys())
    }

    /**
     * 获取连接状态
     */
    get connected() {
        return this.client?.connected || false
    }
}

// 导出单例
export const wsService = new WebSocketService()

// 也可以导出类，供需要多实例的场景使用
export { WebSocketService }

