package com.game.listener;

import com.game.service.UserStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Objects;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    private final StringRedisTemplate redisTemplate;
    private final UserStateService userStateService;

    public WebSocketEventListener(StringRedisTemplate redisTemplate, UserStateService userStateService) {
        this.redisTemplate = redisTemplate;
        this.userStateService = userStateService;
    }

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        // WebSocket连接建立时的处理逻辑可在此扩展
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = (Long) Objects.requireNonNull(accessor.getSessionAttributes()).get("userId");

        if (userId != null) {
            String key = "user:state:" + userId;
            redisTemplate.opsForHash().put(key, "online", "0");
            userStateService.markOffline(userId);
        }
    }
}