package com.game.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.entity.GameRoom;
import com.game.entity.Gomoku;
import com.game.service.WebSocketSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionService sessionService;

    private static final Map<String, Object> EMPTY = Map.of();

    // 从 session 获取 userId
    private Long getUserId(SimpMessageHeaderAccessor accessor) {
        var attrs = accessor.getSessionAttributes();
        return attrs != null && attrs.get("userId") instanceof Long id ? id : null;
    }

    // 验证用户是房间参与者
    private boolean isNotRoomParticipant(String roomCode, Long userId) {
        if (roomCode == null || userId == null) return true;
        return redisTemplate.opsForHash().get("room:players:" + roomCode, userId.toString()) == null;
    }

    // 检查房间是否不存在
    private boolean roomNotExists(String roomCode) {
        return !Boolean.TRUE.equals(redisTemplate.hasKey("room:code:" + roomCode));
    }

    @GetMapping("/api/ws/stats")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "activeSessions", sessionService.getActiveSessionCount(),
                "onlineUsers", sessionService.getOnlineUserCount(),
                "timestamp", System.currentTimeMillis()
        ));
    }

    @MessageMapping("/room/{roomCode}/leave")
    @SendTo("/topic/room/{roomCode}")
    public Map<String, Object> handlePlayerLeave(
            @DestinationVariable String roomCode,
            Map<String, Object> data,
            SimpMessageHeaderAccessor accessor) {

        Long userId = getUserId(accessor);
        if (userId == null || isNotRoomParticipant(roomCode, userId) || roomNotExists(roomCode)) {
            log.warn("玩家退出请求被拒绝: userId={}, roomCode={}", userId, roomCode);
            return EMPTY;
        }

        data.put("type", "playerLeft");
        data.put("userId", userId);
        log.info("玩家退出房间: userId={}, roomCode={}", userId, roomCode);
        return data;
    }

    @MessageMapping("/room/{roomCode}/gameEnd")
    @SendTo("/topic/room/{roomCode}")
    public Map<String, Object> handleGameEnd(
            @DestinationVariable String roomCode,
            Map<String, Object> data,
            SimpMessageHeaderAccessor accessor) {

        Long userId = getUserId(accessor);
        if (userId == null || isNotRoomParticipant(roomCode, userId) || roomNotExists(roomCode)) {
            log.warn("游戏结束请求被拒绝: userId={}, roomCode={}", userId, roomCode);
            return EMPTY;
        }

        data.put("type", "gameEnd");
        data.put("triggeredBy", userId);
        log.info("游戏结束: userId={}, roomCode={}", userId, roomCode);
        return data;
    }

    @MessageMapping("/room/{roomCode}/recover")
    @SendToUser("/queue/recover")
    public Map<String, Object> recoverGameData(
            @DestinationVariable String roomCode,
            Map<String, Object> data,
            SimpMessageHeaderAccessor accessor) {

        Long userId = getUserId(accessor);
        
        // 身份验证
        if (userId == null) {
            log.warn("游戏恢复请求被拒绝: 无法获取用户身份, roomCode={}", roomCode);
            return Map.of("success", false, "message", "身份验证失败");
        }
        if (isNotRoomParticipant(roomCode, userId)) {
            log.warn("游戏恢复请求被拒绝: 用户不是房间参与者, userId={}, roomCode={}", userId, roomCode);
            return Map.of("success", false, "message", "您不是该房间的参与者");
        }

        try {
            String roomKey = "room:code:" + roomCode;
            Object gameRoomObj = redisTemplate.opsForValue().get(roomKey);
            if (gameRoomObj == null) {
                return Map.of("success", false, "message", "房间不存在或游戏已结束");
            }

            Object gomokuObj = redisTemplate.opsForValue().get("gomoku:" + roomCode);
            if (gomokuObj == null) {
                return Map.of("success", false, "message", "游戏数据不存在");
            }

            log.info("游戏数据恢复成功: userId={}, roomCode={}", userId, roomCode);
            return Map.of(
                    "success", true,
                    "message", "游戏数据恢复成功",
                    "gameRoom", objectMapper.convertValue(gameRoomObj, GameRoom.class),
                    "gomoku", objectMapper.convertValue(gomokuObj, Gomoku.class),
                    "players", redisTemplate.opsForHash().entries("room:players:" + roomCode),
                    "timestamp", System.currentTimeMillis()
            );
        } catch (Exception e) {
            log.error("游戏数据恢复失败: roomCode={}, error={}", roomCode, e.getMessage());
            return Map.of("success", false, "message", "恢复游戏数据失败");
        }
    }
}
