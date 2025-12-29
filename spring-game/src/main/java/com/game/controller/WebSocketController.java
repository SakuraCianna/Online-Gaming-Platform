package com.game.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.entity.GameRoom;
import com.game.entity.Gomoku;
import com.game.service.WebSocketSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebSocketSessionService sessionService;

    /**
     * 获取WebSocket连接统计信息
     */
    @GetMapping("/api/ws/stats")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("activeSessions", sessionService.getActiveSessionCount());
        stats.put("onlineUsers", sessionService.getOnlineUserCount());
        stats.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(stats);
    }

    /**
     * 处理玩家退出/认输消息
     * 验证房间状态后才转发消息，避免向已结束的游戏发送消息
     */
    @MessageMapping("/room/{roomCode}/leave")
    @SendTo("/topic/room/{roomCode}")
    public Map<String, Object> handlePlayerLeave(
            @DestinationVariable String roomCode,
            Map<String, Object> data) {

        // 检查房间是否还存在（游戏是否已结束）
        String roomKey = "room:code:" + roomCode;
        Boolean roomExists = redisTemplate.hasKey(roomKey);

        if (Boolean.FALSE.equals(roomExists) || roomExists == null) {
            // 房间已结束或无法检查，返回空消息（不会被转发）
            return Map.of();
        }

        // 确保消息类型为 playerLeft
        data.put("type", "playerLeft");

        // 转发消息到房间topic，由前端处理
        return data;
    }

    /**
     * 处理游戏结束通知
     * 将游戏结束消息广播给房间内所有玩家，确保双方状态同步
     */
    @MessageMapping("/room/{roomCode}/gameEnd")
    @SendTo("/topic/room/{roomCode}")
    public Map<String, Object> handleGameEnd(
            @DestinationVariable String roomCode,
            Map<String, Object> data) {

        // 检查房间是否存在
        String roomKey = "room:code:" + roomCode;
        Boolean roomExists = redisTemplate.hasKey(roomKey);

        if (Boolean.FALSE.equals(roomExists) || roomExists == null) {
            // 房间不存在，返回空消息
            return Map.of();
        }

        // 确保消息类型为 gameEnd
        data.put("type", "gameEnd");

        // 广播游戏结束消息
        return data;
    }

    /**
     * 游戏数据恢复功能
     * 当玩家断线重连后，通过此接口恢复游戏状态
     * 
     * @param roomCode 房间号
     * @param data     请求数据（包含 userId）
     * @return 游戏状态数据
     */
    @MessageMapping("/room/{roomCode}/recover")
    @SendToUser("/queue/recover")
    public Map<String, Object> recoverGameData(
            @DestinationVariable String roomCode,
            Map<String, Object> data) {

        Map<String, Object> result = new HashMap<>();

        try {
            // 检查房间是否存在
            String roomKey = "room:code:" + roomCode;
            Boolean roomExists = redisTemplate.hasKey(roomKey);

            if (!roomExists) {
                result.put("success", false);
                result.put("message", "房间不存在或游戏已结束");
                return result;
            }

            // 获取房间信息
            Object gameRoomObj = redisTemplate.opsForValue().get(roomKey);
            if (gameRoomObj == null) {
                result.put("success", false);
                result.put("message", "房间数据不存在");
                return result;
            }
            GameRoom gameRoom = objectMapper.convertValue(gameRoomObj, GameRoom.class);

            // 获取游戏数据
            String gomokuKey = "gomoku:" + roomCode;
            Object gomokuObj = redisTemplate.opsForValue().get(gomokuKey);
            if (gomokuObj == null) {
                result.put("success", false);
                result.put("message", "游戏数据不存在");
                return result;
            }
            Gomoku gomoku = objectMapper.convertValue(gomokuObj, Gomoku.class);

            // 获取房间玩家信息
            String playersKey = "room:players:" + roomCode;
            Map<Object, Object> playersMap = redisTemplate.opsForHash().entries(playersKey);

            // 构建返回数据
            result.put("success", true);
            result.put("message", "游戏数据恢复成功");
            result.put("gameRoom", gameRoom);
            result.put("gomoku", gomoku);
            result.put("players", playersMap);
            result.put("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "恢复游戏数据失败: " + e.getMessage());
        }

        return result;
    }
}
