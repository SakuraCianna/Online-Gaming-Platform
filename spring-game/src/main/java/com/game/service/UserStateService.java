package com.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserStateService {
    private static final Duration STATE_TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "user:state:";
    private static final String F_ONLINE = "online";
    private static final String F_GAME = "currentGame";

    // 使用 StringRedisTemplate 存储简单字符串
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messaging;

    private String key(long userId) {
        return KEY_PREFIX + userId;
    }

    private record State(boolean online, String game) {
    }

    // 统一读取
    private State readState(long userId) {
        var vals = redisTemplate.opsForHash().multiGet(key(userId), java.util.List.of(F_ONLINE, F_GAME));
        boolean online = vals.get(0) != null && "1".equals(String.valueOf(vals.get(0)));
        String game = (vals.get(1) == null || String.valueOf(vals.get(1)).isBlank())
                ? null
                : String.valueOf(vals.get(1));
        return new State(online, game);
    }

    // 统一拼装载荷
    private Map<String, Object> payload(long userId, State s, boolean includeNullGame) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("userId", userId);
        m.put("online", s.online());
        if (includeNullGame || s.game() != null)
            m.put("currentGame", s.game());
        return m;
    }

    private void broadcast(long userId) {
        State s = readState(userId);
        Map<String, Object> payloadData = payload(userId, s, false);
        messaging.convertAndSend("/topic/presence", payloadData);
    }

    public Map<String, Object> getState(long userId) {
        State s = readState(userId);
        return payload(userId, s, true); // 返回时可包含 null
    }

    public void markOnline(long userId) {
        var k = key(userId);
        var ops = redisTemplate.boundHashOps(k);
        ops.put(F_ONLINE, "1");
        redisTemplate.expire(k, STATE_TTL);
        broadcast(userId);
    }

    public void markOffline(long userId) {
        redisTemplate.delete(key(userId)); // 直接删除整个 key
        broadcast(userId);
    }

    public void heartbeat(long userId) {
        var k = key(userId);
        var ops = redisTemplate.boundHashOps(k);
        ops.putIfAbsent(F_ONLINE, "1"); // 确保存在
        redisTemplate.expire(k, STATE_TTL);
    }

    public void setCurrentGame(long userId, String gameName) {
        var k = key(userId);
        var ops = redisTemplate.boundHashOps(k);
        ops.put(F_GAME, gameName);
        ops.put(F_ONLINE, "1");
        redisTemplate.expire(k, STATE_TTL);
        broadcast(userId);
    }

    public void clearCurrentGame(long userId) {
        var k = key(userId);
        redisTemplate.boundHashOps(k).delete(F_GAME);
        redisTemplate.expire(k, STATE_TTL);
        broadcast(userId);
    }
}