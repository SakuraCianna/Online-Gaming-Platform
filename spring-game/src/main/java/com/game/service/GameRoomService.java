package com.game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.component.RedisKeyManager;
import com.game.entity.GameRoom;
import com.game.entity.RoomPlayer;
import com.game.entity.User;
import com.game.exception.BusinessException;
import com.game.mapper.GameRoomMapper;
import com.game.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class GameRoomService {
    private final GameRoomMapper gameRoomMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisKeyManager redisKeyManager;

    public Map<String, Object> checkRoomCode(String roomCode) {
        String key = redisKeyManager.buildRoomKey(roomCode);
        Boolean has = redisTemplate.hasKey(key);
        Map<String, Object> result = new HashMap<>();
        result.put("exists", has);
        return result;
    }

    public Map<String, Object> createRoom(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        String gameName = (String) request.get("gameName");
        Integer status = request.get("status") != null ? ((Number) request.get("status")).intValue() : 0;
        Integer isPrivate = request.get("isPrivate") != null ? ((Number) request.get("isPrivate")).intValue() : 0;

        // 单机游戏不允许创建房间
        if ("2048".equals(gameName) || "minesweeper".equals(gameName)) {
            throw new BusinessException(400, "单机游戏无需创建房间");
        }

        GameRoom room = new GameRoom();
        room.setRoomCode(roomCode);
        room.setGameName(gameName);
        room.setStatus(status);
        room.setCreatorId(currentUserId);
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
        player.setUserId(currentUserId);
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
            redisTemplate.opsForHash().put(playerKey, String.valueOf(currentUserId), player);
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

    public Map<String, Object> leaveRoom(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        long userId = currentUserId; // 使用当前登录用户
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

    public Map<String, Object> inviteToRoom(Map<String, Object> request, Long currentUserId) {
        long userId = currentUserId; // 使用当前登录用户作为邀请者
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

    public Map<String, Object> acceptInvite(Map<String, Object> request, Long currentUserId) {
        long userId = currentUserId; // 使用当前登录用户
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

    public Map<String, Object> rejectInvite(Map<String, Object> request, Long currentUserId) {
        long userId = currentUserId; // 使用当前登录用户
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
                        long aiUserId = player.getUserId();
                        int aiNumber = (int) (Math.abs(aiUserId) + 1);
                        String aiDisplayName = "AI Player #" + aiNumber;

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

    public Map<String, Object> kickPlayer(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        long kickerId = currentUserId; // 使用当前登录用户作为踢人者
        String kickerName = (String) request.getOrDefault("kickerName", "房主");
        
        // 支持字符串类型的AI ID（如 "ai_1234567890"）
        Object kickedUserIdObj = request.get("kickedUserId");
        String kickedUserIdStr = kickedUserIdObj != null ? kickedUserIdObj.toString() : "0";
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

            // 从 Redis 中删除被踢玩家的信息（使用字符串作为key）
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Long deleted = redisTemplate.opsForHash().delete(playerKey, kickedUserIdStr);

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
            // 尝试转换为long，如果失败则使用字符串（用于AI玩家）
            try {
                kickMessage.put("kickedUserId", Long.parseLong(kickedUserIdStr));
            } catch (NumberFormatException e) {
                kickMessage.put("kickedUserId", kickedUserIdStr);
            }
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

    // 生成AI显示名称（根据当前房间已有AI数量，找到第一个未使用的编号）
    private String generateAIDisplayName(Map<Object, Object> playersData) {
        Set<Integer> usedNumbers = new HashSet<>();

        // 收集所有已使用的AI编号
        for (Object value : playersData.values()) {
            try {
                RoomPlayer player = objectMapper.convertValue(value, RoomPlayer.class);
                if (player.getIsAi() == 1 && player.getUserId() <= 0) {
                    // 从负数ID推算编号：0->1, -1->2, -2->3...
                    int number = (int) (Math.abs(player.getUserId()) + 1);
                    usedNumbers.add(number);
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        // 找到第一个未使用的编号（从1开始）
        int nextNumber = 1;
        while (usedNumbers.contains(nextNumber)) {
            nextNumber++;
        }

        return "AI Player #" + nextNumber;
    }

    // 查找下一个可用的AI ID（0, -1, -2, -3...）
    private long findNextAvailableAIId(String playerKey) {
        Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
        Set<Long> usedIds = new HashSet<>();

        // 收集所有已使用的AI ID
        for (Object key : playersData.keySet()) {
            try {
                long userId = Long.parseLong(key.toString());
                if (userId <= 0) {  // AI ID应该是0或负数
                    usedIds.add(userId);
                }
            } catch (Exception e) {
                // 忽略无效的key（如字符串型AI ID）
            }
        }

        // 从0开始递减查找第一个未使用的ID
        long nextId = 0;
        while (usedIds.contains(nextId)) {
            nextId--;
        }

        return nextId;
    }

    public Map<String, Object> addAI(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        long userId = currentUserId; // 使用当前登录用户
        String aiDifficulty = (String) request.getOrDefault("aiDifficulty", "medium");
        
        // 获取前端传递的目标队伍和座位索引
        Integer targetTeamId = request.get("targetTeamId") != null ? 
            ((Number) request.get("targetTeamId")).intValue() : null;
        Integer targetSeatIndex = request.get("targetSeatIndex") != null ? 
            ((Number) request.get("targetSeatIndex")).intValue() : null;

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

            // 获取当前房间的所有玩家数据（用于队伍分配和AI名称生成）
            Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);

            // 生成AI显示名称（在添加到Redis之前）
            String aiDisplayName = generateAIDisplayName(playersData);

            // 查找下一个可用的AI ID（支持多个AI）
            long aiUserId = findNextAvailableAIId(playerKey);

            // 构造AI玩家对象
            RoomPlayer aiPlayer = new RoomPlayer();
            aiPlayer.setRoomId(null);
            aiPlayer.setUserId(aiUserId); // 使用动态分配的AI ID
            aiPlayer.setIsAi(1); // 标记为AI
            aiPlayer.setAiType(aiDifficulty); // 存储AI难度
            aiPlayer.setAiConfig(null);

            // 根据队伍模式和前端指定的目标位置分配队伍
            int teamMode = room.getTeamMode() != null ? room.getTeamMode() : 0;
            if (targetTeamId != null && targetTeamId > 0) {
                // 如果前端指定了目标队伍，直接使用
                aiPlayer.setTeamId(targetTeamId);
            } else if (teamMode == 1) {
                // 否则使用原有的平衡逻辑
                int team1Count = 0;
                int team2Count = 0;
                for (Object playerObj : playersData.values()) {
                    try {
                        RoomPlayer player = objectMapper.convertValue(playerObj, RoomPlayer.class);
                        if (player.getTeamId() != null) {
                            if (player.getTeamId() == 1) team1Count++;
                            else if (player.getTeamId() == 2) team2Count++;
                        }
                    } catch (Exception e) {
                        // 忽略转换错误
                    }
                }
                // 分配到人数较少的队伍，如果人数相同则分配到队伍1
                aiPlayer.setTeamId(team1Count <= team2Count ? 1 : 2);
            } else {
                // 无队伍模式
                aiPlayer.setTeamId(0);
            }

            aiPlayer.setIsReady(1); // AI自动准备
            aiPlayer.setPlayerRole("ai"); // AI角色
            // 使用目标座位索引作为position（如果提供），否则使用玩家计数
            aiPlayer.setPosition(targetSeatIndex != null ? targetSeatIndex + 1 : playerCount.intValue() + 1);

            aiPlayer.setKill(0);
            aiPlayer.setDeath(0);
            aiPlayer.setAssist(0);
            aiPlayer.setJoinTime(LocalDateTime.now());
            aiPlayer.setLeaveTime(null);

            // 将AI玩家加入房间（使用AI userId作为Key）
            redisTemplate.opsForHash().put(playerKey, String.valueOf(aiUserId), aiPlayer);

            // 刷新所有相关 Key 的 TTL
            redisKeyManager.refreshRoomTTL(roomCode);

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
     * 切换队伍（真人玩家只能切换到空位）
     */
    public Map<String, Object> switchTeam(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        long userId = currentUserId; // 使用当前登录用户
        int targetTeamId = Integer.parseInt(request.get("targetTeamId").toString());

        Map<String, Object> result = new HashMap<>();
        String lockKey = "lock:room:switchTeam:" + roomCode + ":" + userId;

        try {
            // 获取分布式锁，防止并发切换
            Boolean lockAcquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "locked", 3, TimeUnit.SECONDS);
            if (lockAcquired == null || !lockAcquired) {
                result.put("success", false);
                result.put("message", "操作过于频繁，请稍候再试");
                return result;
            }

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
            if (teamMode != 1) {
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
            
            // 检查是否尝试切换到当前队伍
            if (player.getTeamId() != null && player.getTeamId() == targetTeamId) {
                result.put("success", false);
                result.put("message", "您已经在这个队伍中了");
                return result;
            }

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

            // 2v2模式每队最多2人，必须有空位
            if (targetTeamCount >= 2) {
                result.put("success", false);
                result.put("message", "目标队伍没有空位");
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
            switchMessage.put("message", username + " 切换到了队伍" + (targetTeamId == 1 ? "A" : "B"));

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, switchMessage);

            result.put("success", true);
            result.put("message", "切换队伍成功");
            result.put("teamId", targetTeamId);

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "切换队伍失败: " + e.getMessage());
            log.error("切换队伍失败", e);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }

        return result;
    }

    /**
     * 更新房间游戏模式和teamMode
     */
    public Map<String, Object> updateRoomMode(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        long userId = currentUserId; // 使用当前登录用户
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
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
            if (teamMode == 2) {

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

    /**
     * 智能模式切换：随机分配队伍/座位
     * 优化：1. 房主始终在队伍A；2. 返回完整玩家列表信息
     */
    public Map<String, Object> switchMode(Map<String, Object> request, Long currentUserId) {
        String roomCode = (String) request.get("roomCode");
        long userId = currentUserId; // 使用当前登录用户
        String newMode = (String) request.get("mode"); // "1v1v1v1" or "2v2"
        
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
                result.put("message", "只有房主可以切换模式");
                return result;
            }

            // 更新房间模式
            int teamMode = "2v2".equals(newMode) ? 1 : 0;
            room.setTeamMode(teamMode);
            redisTemplate.opsForValue().set(roomKey, room);

            // 获取所有玩家
            String playerKey = redisKeyManager.buildPlayerKey(roomCode);
            if (playerKey == null || playerKey.isEmpty()) {
                throw new BusinessException(500, "玩家Key生成失败");
            }
            Map<Object, Object> playersData = redisTemplate.opsForHash().entries(playerKey);
            
            List<Map<String, Object>> updatedPlayers = new ArrayList<>();
            
            if (!playersData.isEmpty()) {
                List<Map.Entry<Object, Object>> playerEntries = new ArrayList<>(playersData.entrySet());

                if (teamMode == 1) {
                    // 切换到2v2模式：房主固定在队伍1，其他玩家随机分配
                    
                    // 分离房主和其他玩家
                    Map.Entry<Object, Object> ownerEntry = null;
                    List<Map.Entry<Object, Object>> otherEntries = new ArrayList<>();
                    
                    for (Map.Entry<Object, Object> entry : playerEntries) {
                        try {
                            RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                            if (player.getUserId() == room.getCreatorId()) {
                                ownerEntry = entry;
                            } else {
                                otherEntries.add(entry);
                            }
                        } catch (Exception e) {
                            log.error("解析玩家数据失败: {}", e.getMessage());
                        }
                    }
                    
                    // 随机打乱其他玩家
                    Collections.shuffle(otherEntries);
                    
                    int position = 1;
                    
                    // 房主固定在队伍1的第一个位置
                    if (ownerEntry != null && ownerEntry.getKey() != null) {
                        try {
                            RoomPlayer owner = objectMapper.convertValue(ownerEntry.getValue(), RoomPlayer.class);
                            owner.setTeamId(1);
                            owner.setPosition(position++);
                            String ownerKey = ownerEntry.getKey().toString();
                            if (ownerKey != null && !ownerKey.isEmpty()) {
                                redisTemplate.opsForHash().put(playerKey, ownerKey, owner);
                            }
                            updatedPlayers.add(buildPlayerInfo(owner));
                        } catch (Exception e) {
                            log.error("更新房主信息失败: {}", e.getMessage());
                        }
                    }
                    
                    // 其他玩家分配：根据位置交替分配到队伍1和队伍2
                    // 队伍1还有1个位置，队伍2有2个位置
                    int team1Count = 1; // 房主已占1个
                    int team2Count = 0;
                    
                    for (Map.Entry<Object, Object> entry : otherEntries) {
                        try {
                            RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                            
                            // 队伍1最多2人，队伍2最多2人
                            if (team1Count < 2) {
                                player.setTeamId(1);
                                team1Count++;
                            } else if (team2Count < 2) {
                                player.setTeamId(2);
                                team2Count++;
                            } else {
                                // 超过4人的情况，继续交替分配
                                player.setTeamId((position % 2 == 0) ? 1 : 2);
                            }
                            
                            player.setPosition(position++);
                            redisTemplate.opsForHash().put(playerKey, entry.getKey().toString(), player);
                            updatedPlayers.add(buildPlayerInfo(player));
                        } catch (Exception e) {
                            log.error("更新玩家信息失败: {}", e.getMessage());
                        }
                    }
                } else {
                    // 切换到混战模式：清空队伍，随机分配座位

                    Collections.shuffle(playerEntries); // 随机打乱顺序
                    
                    for (int i = 0; i < playerEntries.size(); i++) {
                        Map.Entry<Object, Object> entry = playerEntries.get(i);
                        try {
                            RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                            player.setTeamId(0); // 无队伍
                            player.setPosition(i + 1); // 重新分配座位位置
                            redisTemplate.opsForHash().put(playerKey, entry.getKey().toString(), player);
                            updatedPlayers.add(buildPlayerInfo(player));
                        } catch (Exception e) {
                            log.error("更新玩家信息失败: {}", e.getMessage());
                        }
                    }
                }
            }

            // 刷新TTL
            redisKeyManager.refreshRoomTTL(roomCode);

            // 推送模式切换完成消息（包含完整玩家列表）
            Map<String, Object> switchedMessage = new HashMap<>();
            switchedMessage.put("type", "modeSwitched");
            switchedMessage.put("mode", newMode);
            switchedMessage.put("teamMode", teamMode);
            switchedMessage.put("players", updatedPlayers); // 包含完整的玩家列表
            switchedMessage.put("message", teamMode == 1 ? "已切换到团队模式，房主在队伍A，其他成员已随机分配" : "已切换到混战模式，座位已重新分配");

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, switchedMessage);

            result.put("success", true);
            result.put("message", "模式切换成功");

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "切换模式失败: " + e.getMessage());
            log.error("切换模式异常: {}", e.getMessage(), e);
        }

        return result;
    }
    
    /**
     * 构建玩家信息Map（用于WebSocket消息）
     */
    private Map<String, Object> buildPlayerInfo(RoomPlayer player) {
        Map<String, Object> playerInfo = new HashMap<>();
        
        try {
            if (player.getIsAi() == 1) {
                // AI玩家
                long aiUserId = player.getUserId();
                int aiNumber = (int) (Math.abs(aiUserId) + 1);
                String aiDisplayName = "AI Player #" + aiNumber;
                
                playerInfo.put("userId", aiUserId);
                playerInfo.put("username", aiDisplayName);
                playerInfo.put("avatar", "/image/default-avatar.jpg");
                playerInfo.put("isAi", 1);
                playerInfo.put("aiType", player.getAiType());
            } else {
                // 真人玩家
                User user = userMapper.selectById(player.getUserId());
                if (user != null) {
                    playerInfo.put("userId", player.getUserId());
                    playerInfo.put("username", user.getUsername());
                    playerInfo.put("avatar", user.getAvatar() != null ? user.getAvatar() : "/image/default-avatar.jpg");
                    playerInfo.put("isAi", 0);
                }
            }
            
            // 公共字段
            playerInfo.put("teamId", player.getTeamId());
            playerInfo.put("isReady", player.getIsReady());
            playerInfo.put("playerRole", player.getPlayerRole());
            playerInfo.put("position", player.getPosition());
        } catch (Exception e) {
            log.error("构建玩家信息失败: {}", e.getMessage());
        }
        
        return playerInfo;
    }
}