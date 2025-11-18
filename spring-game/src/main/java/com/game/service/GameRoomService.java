package com.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.component.RedisKeyManager;
import com.game.entity.GameRoom;
import com.game.entity.RoomPlayer;
import com.game.entity.User;
import com.game.exception.BusinessException;
import com.game.mapper.GameRoomMapper;
import com.game.mapper.UserMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class GameRoomService {
    private final GameRoomMapper gameRoomMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisKeyManager redisKeyManager;

    public GameRoomService(GameRoomMapper gameRoomMapper, UserMapper userMapper,
            RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate, RedisKeyManager redisKeyManager) {
        this.gameRoomMapper = gameRoomMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.redisKeyManager = redisKeyManager;
    }

    public Map<String, Object> checkRoomCode(String roomCode) {
        String key = redisKeyManager.buildRoomKey(roomCode);
        Boolean has = redisTemplate.hasKey(key);
        Map<String, Object> result = new HashMap<>();
        result.put("exists", has);
        return result;
    }

    public Map<String, Object> createRoom(Map<String, Object> request) {
        String roomCode = (String) request.get("roomCode");
        String gameName = (String) request.get("gameName");
        Integer status = request.get("status") != null ? ((Number) request.get("status")).intValue() : 0;
        Long creatorId = request.get("creatorId") != null ? Long.valueOf(request.get("creatorId").toString()) : null;
        Integer isPrivate = request.get("isPrivate") != null ? ((Number) request.get("isPrivate")).intValue() : 0;

        // 单机游戏不允许创建房间
        if ("2048".equals(gameName) || "minesweeper".equals(gameName)) {
            throw new BusinessException(400, "单机游戏无需创建房间");
        }

        GameRoom room = new GameRoom();
        room.setRoomCode(roomCode);
        room.setGameName(gameName);
        room.setStatus(status);
        room.setCreatorId(creatorId);
        room.setIsPrivate(isPrivate);

        // 根据游戏类型设置游戏配置（仅多人游戏）
        try {
            switch (gameName) {
                case "gomoku":
                    room.setMaxPlayers(2);
                    room.setMinPlayers(2);
                    room.setTeamMode(1);
                    Map<String, Object> gomokuConfig = new HashMap<>();
                    gomokuConfig.put("boardSize", "17x17");
                    gomokuConfig.put("timeLimit", 0);
                    gomokuConfig.put("blackFirst", true);
                    room.setGameConfig(gomokuConfig);
                    break;

                case "tank_battle":
                    room.setMaxPlayers(4);
                    room.setMinPlayers(4);
                    room.setTeamMode(0); // 无队伍混战
                    Map<String, Object> tankConfig = new HashMap<>();
                    tankConfig.put("map", "classic");
                    tankConfig.put("respawnTime", 3);
                    tankConfig.put("friendlyFire", false);
                    room.setGameConfig(tankConfig);
                    break;

                default:
                    // 其他多人游戏默认配置
                    room.setMaxPlayers(2);
                    room.setMinPlayers(2);
                    room.setTeamMode(0);
                    room.setGameConfig(new HashMap<>());
            }
        } catch (Exception e) {
            throw new BusinessException(500, "游戏配置设置失败: " + e.getMessage());
        }

        // 存储到Redis（房间信息），直接存对象（由 RedisConfig 的 Jackson2 序列化）
        String key = redisKeyManager.buildRoomKey(room.getRoomCode());
        try {
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, room);
            if (Boolean.FALSE.equals(success)) {
                throw new BusinessException(400, "房间码已被占用,请重新生成");
            }
            // 设置房间信息初始 TTL 为 24 小时
            redisKeyManager.setRoomInitialTTL(roomCode);
        } catch (Exception e) {
            throw new BusinessException(500, "创建房间失败: " + e.getMessage());
        }

        // 构造房主的 RoomPlayer 对象
        RoomPlayer player = new RoomPlayer();
        player.setRoomId(null);
        player.setUserId(creatorId);
        player.setIsAi(0);
        player.setAiType(null);
        player.setAiConfig(null);

        // 设置分组和角色相关字段
        player.setTeamId(1); // 房主默认在队伍1
        player.setIsReady(1); // 房主自动准备
        player.setPlayerRole("owner"); // 房主角色
        player.setPosition(1); // 第一位置

        player.setKill(0);
        player.setDeath(0);
        player.setAssist(0);
        player.setJoinTime(LocalDateTime.now());
        player.setLeaveTime(null);

        // 存储到Redis（房间玩家信息，Hash结构），直接存对象
        String playerKey = redisKeyManager.buildPlayerKey(roomCode);
        try {
            redisTemplate.opsForHash().put(playerKey, String.valueOf(creatorId), player);
            // 设置玩家信息初始 TTL 为 24 小时
            redisKeyManager.setPlayerInitialTTL(roomCode);
        } catch (Exception e) {
            // 回滚房间信息
            redisTemplate.delete(key);
            throw new BusinessException(500, "房主玩家信息存储失败: " + e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("roomId", room.getId());
        result.put("room", room);
        result.put("success", true);
        return result;
    }

    public Map<String, Object> leaveRoom(Map<String, Object> request) {
        String roomCode = (String) request.get("roomCode");
        long userId = Long.parseLong(request.get("userId").toString());
        String username = (String) request.getOrDefault("username", "某玩家");

        String roomKey = redisKeyManager.buildRoomKey(roomCode);
        String playerKey = redisKeyManager.buildPlayerKey(roomCode);

        // 获取房间信息
        Object roomObj = redisTemplate.opsForValue().get(roomKey);
        if (roomObj == null) {
            throw new BusinessException(404, "房间不存在");
        }
        GameRoom room = objectMapper.convertValue(roomObj, GameRoom.class);

        Map<String, Object> result = new HashMap<>();

        // 判断是否为房主
        if (userId == room.getCreatorId()) {
            // 房主退出,推送解散消息
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode,
                    Map.of("type", "roomDestroyed", "message", "房主已离开，房间已解散"));
            // 删除房间和玩家集合
            redisTemplate.delete(roomKey);
            redisTemplate.delete(playerKey);

            result.put("roomDestroyed", true);
            result.put("message", "房主已离开,房间已解散");
        } else {
            // 普通成员退出,推送成员离开消息
            messagingTemplate.convertAndSend(
                    "/topic/room/" + roomCode,
                    Map.of(
                            "type", "memberLeft",
                            "userId", userId,
                            "username", username,
                            "message", username + " 已退出房间"));
            redisTemplate.opsForHash().delete(playerKey, String.valueOf(userId));

            // 刷新所有相关 Key 的 TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            result.put("roomDestroyed", false);
            result.put("message", "已退出房间");
        }

        result.put("success", true);
        return result;
    }

    public Map<String, Object> inviteToRoom(Map<String, Object> request) {
        long userId = Long.parseLong(request.get("userId").toString());
        long friendId = Long.parseLong(request.get("friendId").toString());
        String roomCode = (String) request.get("roomCode");
        String gameName = (String) request.get("gameName");

        Map<String, Object> result = new HashMap<>();

        // 检查房间是否存在
        String roomKey = redisKeyManager.buildRoomKey(roomCode);
        Object roomValue = redisTemplate.opsForValue().get(roomKey);
        if (roomValue == null) {
            throw new BusinessException(404, "房间不存在");
        }

        // 检查用户是否在线且空闲
        if (isUserOnlineAndIdle(friendId)) {
            try {
                User inviter = userMapper.selectById(userId);
                if (inviter == null) {
                    result.put("success", false);
                    result.put("message", "邀请者信息不存在");
                    return result;
                }

                // 构建邀请消息
                Map<String, Object> inviteMessage = new HashMap<>();
                inviteMessage.put("inviterId", userId);
                inviteMessage.put("inviterName", inviter.getUsername());
                inviteMessage.put("inviterAvatar",
                        inviter.getAvatar() != null ? inviter.getAvatar() : "/image/default-avatar.jpg");
                inviteMessage.put("roomCode", roomCode);
                inviteMessage.put("gameName", gameName);
                inviteMessage.put("timestamp", System.currentTimeMillis());

                // 通过 WebSocket 推送邀请
                messagingTemplate.convertAndSend("/topic/roomInvite/" + friendId, inviteMessage);

                // 返回成功结果
                result.put("success", true);
                result.put("message", "邀请已发送");

            } catch (Exception e) {
                result.put("success", false);
                result.put("message", "邀请发送失败: " + e.getMessage());
            }
        } else {
            result.put("success", false);
            result.put("message", "用户不在线或正在游戏,邀请失败");
        }
        return result;
    }

    public Map<String, Object> acceptInvite(Map<String, Object> request) {
        long userId = Long.parseLong(request.get("userId").toString());
        String roomCode = (String) request.get("roomCode");
        long inviterId = Long.parseLong(request.get("inviterId").toString());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 检查房间是否存在
            String roomKey = redisKeyManager.buildRoomKey(roomCode);
            Object roomObj = redisTemplate.opsForValue().get(roomKey);
            if (roomObj == null) {
                result.put("success", false);
                result.put("message", "房间已失效");
                return result;
            }
            GameRoom room = objectMapper.convertValue(roomObj, GameRoom.class);

            // 2. 检查房间是否已满
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Long playerCount = redisTemplate.opsForHash().size(playerKey);

            // 使用房间配置的最大玩家数判断
            int maxPlayers = room.getMaxPlayers() != null ? room.getMaxPlayers() : 2;
            if (playerCount >= maxPlayers) {
                // 检查是否有AI玩家（真人玩家可以替换AI）
                List<String> aiKeys = findAIPlayerKeys(playerKey);

                if (!aiKeys.isEmpty()) {
                    // 房间已满但有AI,移除第一个AI为真人玩家让位
                    String aiKeyToRemove = aiKeys.getFirst();
                    redisTemplate.opsForHash().delete(playerKey, aiKeyToRemove);

                    // 推送AI被移除消息
                    Map<String, Object> kickMessage = new HashMap<>();
                    kickMessage.put("type", "memberLeft");
                    kickMessage.put("userId", Long.parseLong(aiKeyToRemove));
                    kickMessage.put("username", "AI Player");
                    kickMessage.put("message", "AI Player 已退出房间，真人玩家加入");

                    messagingTemplate.convertAndSend("/topic/room/" + roomCode, kickMessage);

                    // 重新计算玩家数
                    playerCount = redisTemplate.opsForHash().size(playerKey);
                } else {
                    // 房间已满且没有AI，拒绝加入
                    result.put("success", false);
                    result.put("message", "房间已满");
                    return result;
                }
            }

            // 3. 获取用户信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                result.put("success", false);
                result.put("message", "用户信息不存在");
                return result;
            }

            // 4. 构造玩家对象
            RoomPlayer player = new RoomPlayer();
            player.setRoomId(null);
            player.setUserId(userId);
            player.setIsAi(0);
            player.setAiType(null);
            player.setAiConfig(null);

            // 根据队伍模式分配队伍
            int teamMode = room.getTeamMode() != null ? room.getTeamMode() : 0;
            if (teamMode == 1) {
                // 1v1模式：加入者在队伍2
                player.setTeamId(2);
            } else if (teamMode >= 2) {
                // 多人队伍模式：默认分配到队伍2（可以后续优化为智能分配）
                player.setTeamId(2);
            } else {
                // 无队伍模式
                player.setTeamId(0);
            }

            player.setIsReady(0); // 加入者需要手动准备
            player.setPlayerRole("member"); // 普通成员
            player.setPosition(playerCount.intValue() + 1); // 按加入顺序排位置

            player.setKill(0);
            player.setDeath(0);
            player.setAssist(0);
            player.setJoinTime(LocalDateTime.now());
            player.setLeaveTime(null);

            // 5. 将玩家加入房间（直接存对象）
            redisTemplate.opsForHash().put(playerKey, String.valueOf(userId), player);

            // 刷新所有相关 Key 的 TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            // 6. 推送成员加入消息到房间
            Map<String, Object> joinMessage = new HashMap<>();
            joinMessage.put("type", "memberJoined");
            joinMessage.put("userId", userId);
            joinMessage.put("username", user.getUsername());
            joinMessage.put("avatar", user.getAvatar() != null ? user.getAvatar() : "/image/default-avatar.jpg");
            joinMessage.put("teamId", player.getTeamId());
            joinMessage.put("position", player.getPosition());
            joinMessage.put("message", user.getUsername() + " 已加入房间");

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, joinMessage);

            // 7. 返回成功结果
            result.put("success", true);
            result.put("room", room);
            result.put("message", "加入房间成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "加入房间失败: " + e.getMessage());
        }

        return result;
    }

    public Map<String, Object> rejectInvite(Map<String, Object> request) {
        long userId = Long.parseLong(request.get("userId").toString());
        String roomCode = (String) request.get("roomCode");
        long inviterId = Long.parseLong(request.get("inviterId").toString());

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 获取拒绝者信息
            User user = userMapper.selectById(userId);
            if (user == null) {
                result.put("success", false);
                result.put("message", "用户信息不存在");
                return result;
            }

            // 2. 通知邀请者被拒绝
            Map<String, Object> rejectMessage = new HashMap<>();
            rejectMessage.put("type", "inviteRejected");
            rejectMessage.put("userId", userId);
            rejectMessage.put("username", user.getUsername());
            rejectMessage.put("roomCode", roomCode);
            rejectMessage.put("message", user.getUsername() + " 拒绝了你的邀请");

            messagingTemplate.convertAndSend("/topic/user/" + inviterId, rejectMessage);

            // 3. 返回成功结果
            result.put("success", true);
            result.put("message", "已拒绝邀请");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "操作失败: " + e.getMessage());
        }

        return result;
    }

    public Map<String, Object> getRoomPlayers(String roomCode) {
        Map<String, Object> result = new HashMap<>();

        try {
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);

            if (playersData.isEmpty()) {
                result.put("success", true);
                result.put("players", new ArrayList<>());
                return result;
            }

            List<Map<String, Object>> players = new ArrayList<>();

            for (Map.Entry<Object, Object> entry : playersData.entrySet()) {
                try {
                    RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                    Map<String, Object> playerInfo = new HashMap<>();

                    // 判断是否为AI玩家
                    if (player.getIsAi() == 1) {
                        // AI玩家
                        long aiUserId = player.getUserId();
                        String aiDisplayName = aiUserId == 0 ? "AI Player" : "AI Player #" + Math.abs(aiUserId);

                        playerInfo.put("userId", aiUserId);
                        playerInfo.put("username", aiDisplayName);
                        playerInfo.put("avatar", "/image/default-avatar.jpg");
                        playerInfo.put("isAi", 1);
                        playerInfo.put("aiType", player.getAiType());
                    } else {
                        // 真人玩家
                        User user = userMapper.selectById(player.getUserId());
                        if (user == null) {
                            continue; // 用户不存在，跳过
                        }

                        playerInfo.put("userId", player.getUserId());
                        playerInfo.put("username", user.getUsername());
                        playerInfo.put("avatar",
                                user.getAvatar() != null ? user.getAvatar() : "/image/default-avatar.jpg");
                        playerInfo.put("isAi", 0);
                    }

                    // 公共字段
                    playerInfo.put("teamId", player.getTeamId());
                    playerInfo.put("isReady", player.getIsReady());
                    playerInfo.put("playerRole", player.getPlayerRole());
                    playerInfo.put("position", player.getPosition());
                    playerInfo.put("joinTime", player.getJoinTime());

                    players.add(playerInfo);
                } catch (Exception e) {
                    System.err.println("解析玩家数据失败: " + e.getMessage());
                }
            }

            result.put("success", true);
            result.put("players", players);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取玩家列表失败: " + e.getMessage());
        }

        return result;
    }

    public Map<String, Object> kickPlayer(Map<String, Object> request) {
        String roomCode = (String) request.get("roomCode");
        long kickerId = Long.parseLong(request.get("kickerId").toString());
        String kickerName = (String) request.getOrDefault("kickerName", "房主");
        long kickedUserId = Long.parseLong(request.get("kickedUserId").toString());
        String kickedUserName = (String) request.getOrDefault("kickedUserName", "玩家");

        Map<String, Object> result = new HashMap<>();

        try {
            // 检查房间是否存在
            String roomKey = "room:code:" + roomCode;
            Object roomObj = redisTemplate.opsForValue().get(roomKey);
            if (roomObj == null) {
                result.put("success", false);
                result.put("message", "房间不存在");
                return result;
            }
            GameRoom room = objectMapper.convertValue(roomObj, GameRoom.class);

            // 验证踢人者是否为房主
            if (room.getCreatorId() != kickerId) {
                result.put("success", false);
                result.put("message", "只有房主可以踢出玩家");
                return result;
            }

            // 从 Redis 中删除被踢玩家的信息
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Long deleted = redisTemplate.opsForHash().delete(playerKey, String.valueOf(kickedUserId));

            if (deleted == 0) {
                result.put("success", false);
                result.put("message", "该玩家不在房间中");
                return result;
            }

            // 刷新所有相关 Key 的 TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            // 构建踢出消息
            Map<String, Object> kickMessage = new HashMap<>();
            kickMessage.put("type", "kicked");
            kickMessage.put("kickerId", kickerId);
            kickMessage.put("kickerName", kickerName);
            kickMessage.put("kickedUserId", kickedUserId);
            kickMessage.put("kickedUserName", kickedUserName);
            kickMessage.put("message", kickedUserName + " 已被移出房间");

            // 广播到房间内所有成员
            messagingTemplate.convertAndSend("/topic/room/" + roomCode, kickMessage);

            // 返回成功结果
            result.put("success", true);
            result.put("message", "已将玩家移出房间");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "踢出玩家失败: " + e.getMessage());
        }

        return result;
    }

    private boolean isUserOnlineAndIdle(long userId) {
        String userKey = redisKeyManager.buildUserStateKey(userId);
        Object onlineObj = redisTemplate.opsForHash().get(userKey, "online");
        Object currentGameObj = redisTemplate.opsForHash().get(userKey, "currentGame");

        boolean isOnline = onlineObj != null && "1".equals(onlineObj.toString());
        boolean isIdle = currentGameObj == null
                || currentGameObj.toString().isEmpty()
                || "null".equals(currentGameObj.toString());

        return isOnline && isIdle;
    }

    // 查找所有AI玩家的Key
    private List<String> findAIPlayerKeys(String playerKey) {
        List<String> aiKeys = new ArrayList<>();
        Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);

        for (Map.Entry<Object, Object> entry : playersData.entrySet()) {
            try {
                RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                if (player.getIsAi() == 1) {
                    aiKeys.add(entry.getKey().toString());
                }
            } catch (Exception e) {
                // 静默处理解析错误
            }
        }

        return aiKeys;
    }

    // 查找下一个可用的AI ID（0, -1, -2, -3...）
    private long findNextAvailableAIId(String playerKey) {
        Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
        long nextId = 0;

        for (Object key : playersData.keySet()) {
            try {
                long userId = Long.parseLong(key.toString());
                if (userId <= nextId) {
                    nextId = userId - 1;
                }
            } catch (Exception e) {
                // 忽略无效的key
            }
        }

        return nextId;
    }

    public Map<String, Object> addAI(Map<String, Object> request) {
        String roomCode = (String) request.get("roomCode");
        long userId = Long.parseLong(request.get("userId").toString());
        String aiDifficulty = (String) request.getOrDefault("aiDifficulty", "medium");

        Map<String, Object> result = new HashMap<>();

        try {
            // 检查房间是否存在
            String roomKey = "room:code:" + roomCode;
            Object roomObj = redisTemplate.opsForValue().get(roomKey);
            if (roomObj == null) {
                result.put("success", false);
                result.put("message", "房间不存在");
                return result;
            }
            GameRoom room = objectMapper.convertValue(roomObj, GameRoom.class);

            // 验证是否为房主
            if (room.getCreatorId() != userId) {
                result.put("success", false);
                result.put("message", "只有房主可以添加AI");
                return result;
            }

            // 检查房间是否已满
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Long playerCount = redisTemplate.opsForHash().size(playerKey);
            int maxPlayers = room.getMaxPlayers() != null ? room.getMaxPlayers() : 2;
            if (playerCount >= maxPlayers) {
                result.put("success", false);
                result.put("message", "房间已满");
                return result;
            }

            // 查找下一个可用的AI ID（支持多个AI）
            long aiUserId = findNextAvailableAIId(playerKey);

            // 构造AI玩家对象
            RoomPlayer aiPlayer = new RoomPlayer();
            aiPlayer.setRoomId(null);
            aiPlayer.setUserId(aiUserId); // 使用动态分配的AI ID
            aiPlayer.setIsAi(1); // 标记为AI
            aiPlayer.setAiType(aiDifficulty); // 存储AI难度
            aiPlayer.setAiConfig(null);

            // 根据队伍模式分配队伍
            int teamMode = room.getTeamMode() != null ? room.getTeamMode() : 0;
            if (teamMode == 1) {
                // 1v1模式：AI在队伍2
                aiPlayer.setTeamId(2);
            } else {
                // 无队伍模式
                aiPlayer.setTeamId(0);
            }

            aiPlayer.setIsReady(1); // AI自动准备
            aiPlayer.setPlayerRole("ai"); // AI角色
            aiPlayer.setPosition(playerCount.intValue() + 1);

            aiPlayer.setKill(0);
            aiPlayer.setDeath(0);
            aiPlayer.setAssist(0);
            aiPlayer.setJoinTime(LocalDateTime.now());
            aiPlayer.setLeaveTime(null);

            // 将AI玩家加入房间（使用AI userId作为Key）
            redisTemplate.opsForHash().put(playerKey, String.valueOf(aiUserId), aiPlayer);

            // 刷新所有相关 Key 的 TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            // 生成AI显示名称
            String aiDisplayName = aiUserId == 0 ? "AI Player" : "AI Player #" + Math.abs(aiUserId);

            // 推送AI加入消息到房间
            Map<String, Object> joinMessage = new HashMap<>();
            joinMessage.put("type", "memberJoined");
            joinMessage.put("userId", aiUserId);
            joinMessage.put("username", aiDisplayName);
            joinMessage.put("avatar", "/image/default-avatar.jpg");
            joinMessage.put("teamId", aiPlayer.getTeamId());
            joinMessage.put("position", aiPlayer.getPosition());
            joinMessage.put("isAI", true);
            joinMessage.put("aiDifficulty", aiDifficulty);
            joinMessage.put("message", aiDisplayName + " 已加入房间");

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, joinMessage);

            result.put("success", true);
            result.put("message", "AI已加入房间");
            result.put("aiUserId", aiUserId);
            result.put("aiDisplayName", aiDisplayName);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "添加AI失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 切换玩家队伍
     */
    public Map<String, Object> switchTeam(Map<String, Object> request) {
        String roomCode = (String) request.get("roomCode");
        long userId = Long.parseLong(request.get("userId").toString());
        int targetTeamId = Integer.parseInt(request.get("teamId").toString());

        Map<String, Object> result = new HashMap<>();

        try {
            // 检查房间是否存在
            String roomKey = "room:code:" + roomCode;
            Object roomObj = redisTemplate.opsForValue().get(roomKey);
            if (roomObj == null) {
                result.put("success", false);
                result.put("message", "房间不存在");
                return result;
            }
            GameRoom room = objectMapper.convertValue(roomObj, GameRoom.class);

            // 只有2v2模式才允许切换队伍
            int teamMode = room.getTeamMode() != null ? room.getTeamMode() : 0;
            if (teamMode != 2) {
                result.put("success", false);
                result.put("message", "当前模式不支持切换队伍");
                return result;
            }

            // 获取玩家信息
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Object playerObj = redisTemplate.opsForHash().get(playerKey, String.valueOf(userId));
            if (playerObj == null) {
                result.put("success", false);
                result.put("message", "玩家不在房间中");
                return result;
            }

            RoomPlayer player = objectMapper.convertValue(playerObj, RoomPlayer.class);

            // 检查目标队伍人数
            Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
            int targetTeamCount = 0;
            for (Object value : playersData.values()) {
                try {
                    RoomPlayer p = objectMapper.convertValue(value, RoomPlayer.class);
                    if (p.getTeamId() != null && p.getTeamId() == targetTeamId) {
                        targetTeamCount++;
                    }
                } catch (Exception e) {
                    // 忽略解析错误
                }
            }

            // 2v2模式每队最多2人
            if (targetTeamCount >= 2) {
                result.put("success", false);
                result.put("message", "目标队伍已满(2/2)");
                return result;
            }

            // 更新玩家队伍
            player.setTeamId(targetTeamId);
            redisTemplate.opsForHash().put(playerKey, String.valueOf(userId), player);

            // 刷新TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            // 获取用户信息用于推送
            User user = userMapper.selectById(userId);
            String username = user != null ? user.getUsername() : "玩家";

            // 推送队伍切换消息
            Map<String, Object> switchMessage = new HashMap<>();
            switchMessage.put("type", "teamSwitched");
            switchMessage.put("userId", userId);
            switchMessage.put("username", username);
            switchMessage.put("teamId", targetTeamId);
            switchMessage.put("message", username + " 加入了队伍" + (targetTeamId == 1 ? "A" : "B"));

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, switchMessage);

            result.put("success", true);
            result.put("message", "切换队伍成功");
            result.put("teamId", targetTeamId);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "切换队伍失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 更新房间游戏模式和teamMode
     */
    public Map<String, Object> updateRoomMode(Map<String, Object> request) {
        String roomCode = (String) request.get("roomCode");
        long userId = Long.parseLong(request.get("userId").toString());
        String mode = (String) request.get("mode"); // "1v1v1v1" or "2v2"

        Map<String, Object> result = new HashMap<>();

        try {
            // 检查房间是否存在
            String roomKey = "room:code:" + roomCode;
            Object roomObj = redisTemplate.opsForValue().get(roomKey);
            if (roomObj == null) {
                result.put("success", false);
                result.put("message", "房间不存在");
                return result;
            }
            GameRoom room = objectMapper.convertValue(roomObj, GameRoom.class);

            // 验证是否为房主
            if (room.getCreatorId() != userId) {
                result.put("success", false);
                result.put("message", "只有房主可以更改游戏模式");
                return result;
            }

            // 根据前端模式字符串设置teamMode
            int teamMode = 0; // 默认为混战模式
            if ("2v2".equals(mode)) {
                teamMode = 2;
            }


            // 更新房间teamMode
            room.setTeamMode(teamMode);
            redisTemplate.opsForValue().set(roomKey, room);

            // 如果切换到2v2模式，自动分配玩家队伍
            if (teamMode == 2) {
                String playerKey = redisKeyManager.buildPlayerKey(roomCode);
                Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
                
                int assignedTeam = 1; // 从队伍1开始分配
                
                for (Map.Entry<Object, Object> entry : playersData.entrySet()) {
                    try {
                        RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                        
                        // 房主固定在队伍1
                        if (Objects.equals(player.getUserId(), room.getCreatorId())) {
                            player.setTeamId(1);
                        } else {
                            // 其他玩家交替分配：1, 2, 1, 2
                            player.setTeamId(assignedTeam);
                            assignedTeam = (assignedTeam == 1) ? 2 : 1;
                        }
                        
                        redisTemplate.opsForHash().put(playerKey, entry.getKey().toString(), player);
                    } catch (Exception e) {
                        // 忽略解析错误
                    }
                }
            } else {
                // 切换到混战模式，清空所有队伍分配
                String playerKey = redisKeyManager.buildPlayerKey(roomCode);
                Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
                
                for (Map.Entry<Object, Object> entry : playersData.entrySet()) {
                    try {
                        RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                        player.setTeamId(0); // 无队伍
                        redisTemplate.opsForHash().put(playerKey, entry.getKey().toString(), player);
                    } catch (Exception e) {
                        // 忽略解析错误
                    }
                }
            }

            // 刷新TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            // 推送模式更新消息
            Map<String, Object> modeUpdateMessage = new HashMap<>();
            modeUpdateMessage.put("type", "modeUpdated");
            modeUpdateMessage.put("mode", mode);
            modeUpdateMessage.put("teamMode", teamMode);
            modeUpdateMessage.put("message", "游戏模式已更新为: " + (teamMode == 2 ? "2v2团队模式" : "混战模式"));

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, modeUpdateMessage);

            result.put("success", true);
            result.put("message", "游戏模式已更新");
            result.put("teamMode", teamMode);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新模式失败: " + e.getMessage());
        }

        return result;
    }
}