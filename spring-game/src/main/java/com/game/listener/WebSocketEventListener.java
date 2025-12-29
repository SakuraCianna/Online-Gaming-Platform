package com.game.listener;

import com.game.service.UserStateService;
import com.game.service.WebSocketSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * WebSocket事件监听器
 * 处理连接、断开、订阅等事件
 * 实现心跳检测和僵尸连接清理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final StringRedisTemplate redisTemplate;
    private final UserStateService userStateService;
    private final WebSocketSessionService sessionService;

    /**
     * 连接建立事件（握手完成前）
     */
    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        log.debug("WebSocket连接请求: sessionId={}", sessionId);
    }

    /**
     * 连接成功事件（握手完成后）
     */
    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        if (accessor.getSessionAttributes() != null) {
            Long userId = (Long) accessor.getSessionAttributes().get("userId");
            
            if (userId != null) {
                // 注册会话
                sessionService.registerSession(sessionId, userId);
                // 标记用户在线
                userStateService.markOnline(userId);
                log.info("WebSocket连接成功: userId={}, sessionId={}", userId, sessionId);
            }
        }
    }

    /**
     * 订阅事件
     */
    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        
        if (accessor.getSessionAttributes() != null) {
            Long userId = (Long) accessor.getSessionAttributes().get("userId");
            log.debug("WebSocket订阅: userId={}, sessionId={}, destination={}", userId, sessionId, destination);
            
            // 更新会话活跃时间
            if (sessionId != null) {
                sessionService.updateLastActiveTime(sessionId);
            }
        }
    }

    /**
     * 断开连接事件
     */
    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        
        Long userId = null;
        if (accessor.getSessionAttributes() != null) {
            userId = (Long) accessor.getSessionAttributes().get("userId");
        }

        if (userId != null) {
            // 注销会话
            sessionService.unregisterSession(sessionId);
            
            // 检查用户是否还有其他活跃会话
            if (!sessionService.hasActiveSessions(userId)) {
                // 没有其他会话，标记离线
                String key = "user:state:" + userId;
                redisTemplate.opsForHash().put(key, "online", "0");
                userStateService.markOffline(userId);
                log.info("WebSocket断开连接，用户离线: userId={}, sessionId={}", userId, sessionId);
            } else {
                log.info("WebSocket断开连接，用户仍有其他会话: userId={}, sessionId={}", userId, sessionId);
            }
        } else {
            log.debug("WebSocket断开连接: sessionId={} (无用户信息)", sessionId);
        }
    }
}