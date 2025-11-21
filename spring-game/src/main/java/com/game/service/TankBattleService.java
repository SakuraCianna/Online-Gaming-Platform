package com.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.component.RedisKeyManager;
import com.game.entity.GameRoom;
import com.game.entity.RoomPlayer;
import com.game.entity.TankBattle;
import com.game.entity.User;
import com.game.exception.BusinessException;
import com.game.mapper.TankBattleMapper;
import com.game.mapper.UserMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class TankBattleService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisKeyManager redisKeyManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final TankBattleMapper tankBattleMapper;

    public TankBattleService(RedisTemplate<String, Object> redisTemplate, RedisKeyManager redisKeyManager, SimpMessagingTemplate messagingTemplate, UserMapper userMapper, ObjectMapper objectMapper, TankBattleMapper tankBattleMapper) {
        this.redisTemplate = redisTemplate;
        this.redisKeyManager = redisKeyManager;
        this.messagingTemplate = messagingTemplate;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.tankBattleMapper = tankBattleMapper;
    }

    // 开始游戏
    public Map<String, Object> startGame(String roomCode, Map<String, Object> request, Long currentUserId) {
        Map<String, Object> result = new HashMap<>();
        try {
            long userId = currentUserId; // 使用当前登录用户
            int mode = Integer.parseInt(request.get("mode").toString());
            String map = request.get("map").toString();

            String roomKey = redisKeyManager.buildRoomKey(roomCode);
            Object gameRoomObj = redisTemplate.opsForValue().get(roomKey);
            if (gameRoomObj == null) {
                throw new BusinessException(404, "房间不存在");
            }

            GameRoom gameRoom = objectMapper.convertValue(gameRoomObj, GameRoom.class);
            if(gameRoom.getCreatorId() != userId) {
                throw new BusinessException(403, "开始游戏发起人不是房主!");
            }
            if (gameRoom.getStatus() != 0) {
                throw new BusinessException(400, "游戏已开始");
            }
            
            // 验证玩家数量必须为4
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Long playerCount = redisTemplate.opsForHash().size(playerKey);
            if (playerCount == null || playerCount != 4) {
                throw new BusinessException(400, "游戏必须4人才能开始，当前玩家数: " + playerCount);
            }
            
            gameRoom.setStartTime(LocalDateTime.now());
            gameRoom.setStatus(1);
            gameRoom.setPlayers(4);
            gameRoom.setTeamMode(mode);

            // 重新存回redis
            redisTemplate.opsForValue().set(roomKey, gameRoom);

            // 创建tankBattle对象
            TankBattle tankBattle = new TankBattle();
            tankBattle.setStartTime(LocalDateTime.now());

            String tankBattleKey = redisKeyManager.buildGameKey(roomCode, "tank_battle");
            redisTemplate.opsForValue().set(tankBattleKey, tankBattle);

            redisKeyManager.setGameInitialTTL(roomCode, "tank_battle");
            redisKeyManager.setRoomInitialTTL(roomCode);
            redisKeyManager.setPlayerInitialTTL(roomCode);

            // 获取所有玩家信息（playerKey已在上面验证时获取）
            Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
            List<Map<String, Object>> players = new ArrayList<>();

            for(Map.Entry<Object, Object> entry : playersData.entrySet()) {
                try {
                    RoomPlayer roomPlayer = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                    Map<String, Object> playerInfo = new HashMap<>();
                    if(roomPlayer.getIsAi() == 1) {
                        long aiUserId = roomPlayer.getUserId();
                        String AIName = "AI Player #" + Math.abs(aiUserId);

                        playerInfo.put("id", aiUserId);
                        playerInfo.put("username", AIName);
                        playerInfo.put("isAI", true);
                        playerInfo.put("team", roomPlayer.getTeamId() != null ? roomPlayer.getTeamId() : 0);
                        playerInfo.put("position", roomPlayer.getPosition() != null ? roomPlayer.getPosition() : 0);
                    } else {
                        User user = userMapper.selectById(roomPlayer.getUserId());
                        if (user != null) {
                            playerInfo.put("id", user.getId());
                            playerInfo.put("username", user.getUsername());
                            playerInfo.put("isAI", false);
                            playerInfo.put("team", roomPlayer.getTeamId() != null ? roomPlayer.getTeamId() : 0);
                            playerInfo.put("position", roomPlayer.getPosition() != null ? roomPlayer.getPosition() : 0);
                            playerInfo.put("avatar", user.getAvatar() != null ? user.getAvatar() : "/image/default-avatar.jpg");
                        }
                    }
                    if (!playerInfo.isEmpty()) {
                        players.add(playerInfo);
                    }
                } catch (Exception e) {
                    System.err.println("解析玩家信息失败: " + e.getMessage());
                }
            }

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "gameStart");
            notification.put("roomCode", roomCode);
            notification.put("mode", mode);
            notification.put("map", map);
            notification.put("players", players);
            notification.put("timestamp", System.currentTimeMillis());

            // 发送WebSocket通知
            messagingTemplate.convertAndSend("/topic/tankbattle/" + roomCode + "/gameStart", notification);

            result.put("success", true);
            result.put("message", "游戏已开始");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "开始游戏失败: " + e.getMessage());
        }
        return result;
    }

    // 分页查询用户游戏记录
    @Transactional(readOnly = true)
    public Map<String, Object> getRecordsByUserId(Long userId, Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();

        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 20; // 默认每页20条
        }

        try {
            // 计算偏移量
            int offset = (page - 1) * size;

            // 查询记录
            List<TankBattle> records = tankBattleMapper.selectByUserIdWithPage(userId, offset, size);

            // 查询总数
            Integer total = tankBattleMapper.countByUserId(userId);

            result.put("success", true);
            result.put("records", records);
            result.put("total", total != null ? total : 0);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", total != null ? (int) Math.ceil((double) total / size) : 0);
        } catch (Exception e) {
            throw new BusinessException(500, "查询游戏记录失败：" + e.getMessage());
        }

        return result;
    }


}