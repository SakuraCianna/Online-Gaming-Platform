package com.game.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket会话管理服务
 * 负责会话注册、心跳检测、僵尸连接清理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketSessionService {

    private final StringRedisTemplate redisTemplate;

    // 本地会话缓存：sessionId -> SessionInfo
    private final Map<String, SessionInfo> localSessions = new ConcurrentHashMap<>();
    
    // 用户会话映射：userId -> Set<sessionId>
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();

    // 心跳超时时间（30秒）
    private static final long HEARTBEAT_TIMEOUT_MS = 30 * 1000;
    
    // Redis会话Key前缀
    private static final String SESSION_KEY_PREFIX = "ws:session:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(5);

    /**
     * 会话信息内部类
     */
    private static class SessionInfo {
        Long userId;
        long lastActiveTime;
        long connectTime;

        SessionInfo(Long userId) {
            this.userId = userId;
            this.lastActiveTime = System.currentTimeMillis();
            this.connectTime = System.currentTimeMillis();
        }
    }

    /**
     * 注册新会话
     */
    public void registerSession(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return;
        }

        // 本地缓存
        SessionInfo info = new SessionInfo(userId);
        localSessions.put(sessionId, info);

        // 用户会话映射
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);

        // Redis存储（用于分布式场景）
        String key = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForHash().put(key, "userId", String.valueOf(userId));
        redisTemplate.opsForHash().put(key, "connectTime", String.valueOf(info.connectTime));
        redisTemplate.opsForHash().put(key, "lastActiveTime", String.valueOf(info.lastActiveTime));
        redisTemplate.expire(key, SESSION_TTL);

        log.debug("注册WebSocket会话: sessionId={}, userId={}", sessionId, userId);
    }

    /**
     * 注销会话
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        SessionInfo info = localSessions.remove(sessionId);
        
        if (info != null && info.userId != null) {
            Set<String> sessions = userSessions.get(info.userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(info.userId);
                }
            }
        }

        // 删除Redis记录
        redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);

        log.debug("注销WebSocket会话: sessionId={}", sessionId);
    }

    /**
     * 更新会话活跃时间
     */
    public void updateLastActiveTime(String sessionId) {
        SessionInfo info = localSessions.get(sessionId);
        if (info != null) {
            info.lastActiveTime = System.currentTimeMillis();
            
            // 更新Redis
            String key = SESSION_KEY_PREFIX + sessionId;
            redisTemplate.opsForHash().put(key, "lastActiveTime", String.valueOf(info.lastActiveTime));
            redisTemplate.expire(key, SESSION_TTL);
        }
    }

    /**
     * 检查用户是否有活跃会话
     */
    public boolean hasActiveSessions(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * 获取用户的所有会话ID
     */
    public Set<String> getUserSessions(Long userId) {
        return userSessions.getOrDefault(userId, Set.of());
    }

    /**
     * 获取会话对应的用户ID
     */
    public Long getUserIdBySession(String sessionId) {
        SessionInfo info = localSessions.get(sessionId);
        return info != null ? info.userId : null;
    }

    /**
     * 获取当前活跃会话数
     */
    public int getActiveSessionCount() {
        return localSessions.size();
    }

    /**
     * 获取当前在线用户数
     */
    public int getOnlineUserCount() {
        return userSessions.size();
    }

    /**
     * 定时清理僵尸连接（每30秒执行一次）
     * 注意：这里只清理本地缓存中的过期记录
     * 实际的WebSocket断开由Spring框架的心跳机制处理
     */
    @Scheduled(fixedRate = 30000)
    public void cleanupZombieSessions() {
        long now = System.currentTimeMillis();
        
        // 先收集需要清理的sessionId
        Set<String> sessionsToClean = new java.util.HashSet<>();

        for (Map.Entry<String, SessionInfo> entry : localSessions.entrySet()) {
            SessionInfo info = entry.getValue();
            // 超时时间设为心跳超时的2倍，避免误清理
            if (now - info.lastActiveTime > HEARTBEAT_TIMEOUT_MS * 2) {
                sessionsToClean.add(entry.getKey());
            }
        }

        // 批量清理本地缓存（不影响实际连接，实际连接由Spring心跳机制管理）
        for (String sessionId : sessionsToClean) {
            SessionInfo info = localSessions.get(sessionId);
            if (info != null) {
                log.warn("清理过期会话缓存: sessionId={}, userId={}, 最后活跃时间={}ms前",
                        sessionId, info.userId, now - info.lastActiveTime);
            }
            // 只清理本地缓存，不触发用户离线逻辑（避免与正常断开事件冲突）
            localSessions.remove(sessionId);
            if (info != null && info.userId != null) {
                Set<String> sessions = userSessions.get(info.userId);
                if (sessions != null) {
                    sessions.remove(sessionId);
                    if (sessions.isEmpty()) {
                        userSessions.remove(info.userId);
                    }
                }
            }
            redisTemplate.delete(SESSION_KEY_PREFIX + sessionId);
        }

        if (!sessionsToClean.isEmpty()) {
            log.info("过期会话缓存清理完成: 清理数量={}, 剩余会话={}", sessionsToClean.size(), localSessions.size());
        }
    }

    /**
     * 定时输出会话统计（每分钟）
     */
    @Scheduled(fixedRate = 60000)
    public void logSessionStats() {
        log.info("WebSocket会话统计: 活跃会话={}, 在线用户={}", 
                getActiveSessionCount(), getOnlineUserCount());
    }
}
