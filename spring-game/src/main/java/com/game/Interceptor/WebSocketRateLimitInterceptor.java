package com.game.Interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * WebSocket消息限流拦截器
 * 防止恶意客户端发送大量消息攻击服务器
 * 限制：单个用户最多100条消息/秒
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketRateLimitInterceptor implements ChannelInterceptor {

    private final StringRedisTemplate redisTemplate;

    // 限流配置
    private static final int MAX_MESSAGES_PER_SECOND = 100;
    private static final String RATE_LIMIT_KEY_PREFIX = "ws:rate:";

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor == null) {
            return message;
        }

        // 只对SEND命令进行限流（客户端发送的消息）
        if (StompCommand.SEND.equals(accessor.getCommand())) {
            Long userId = getUserIdFromSession(accessor);
            
            if (userId != null && !checkRateLimit(userId)) {
                log.warn("用户 {} 消息发送频率超限，已拦截", userId);
                // 返回null会阻止消息发送
                return null;
            }
        }

        return message;
    }

    /**
     * 从Session中获取用户ID
     */
    private Long getUserIdFromSession(StompHeaderAccessor accessor) {
        var attrs = accessor.getSessionAttributes();
        if (attrs != null) {
            Object userId = attrs.get("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            }
        }
        return null;
    }

    /**
     * 检查用户是否超过限流阈值
     * 使用Redis滑动窗口计数器实现
     * @return true=允许发送, false=超限
     */
    private boolean checkRateLimit(Long userId) {
        if (userId == null) {
            return true; // 未登录用户放行（握手阶段会拦截）
        }
        
        String key = RATE_LIMIT_KEY_PREFIX + userId;
        
        try {
            // 使用INCR原子操作增加计数
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count == null) {
                return true;
            }
            
            // 第一次请求时设置过期时间（1秒）
            if (count == 1) {
                redisTemplate.expire(key, 1, TimeUnit.SECONDS);
            }
            
            // 检查是否超限
            return count <= MAX_MESSAGES_PER_SECOND;
            
        } catch (Exception e) {
            log.error("限流检查失败: {}", e.getMessage());
            // 出错时放行，避免影响正常使用
            return true;
        }
    }
}
