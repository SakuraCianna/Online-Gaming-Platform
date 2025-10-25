package com.game.component;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class RedisKeyManager {

    private static final long ROOM_TTL_HOURS = 24;
    private static final long PLAYER_TTL_HOURS = 24;
    private static final long GAME_TTL_HOURS = 1;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisKeyManager(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 刷新房间和玩家信息的 TTL（不刷新游戏数据）
     * 用于房间操作但游戏未开始的场景
     *
     * @param roomCode 房间号
     */
    public void refreshRoomTTL(String roomCode) {
        String roomKey = "room:code:" + roomCode;
        String playerKey = "room:players:" + roomCode;

        if (redisTemplate.hasKey(roomKey)) {
            redisTemplate.expire(roomKey, ROOM_TTL_HOURS, TimeUnit.HOURS);
        }

        if (redisTemplate.hasKey(playerKey)) {
            redisTemplate.expire(playerKey, PLAYER_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * 刷新房间、玩家和游戏相关所有 Key 的 TTL
     * 用于游戏进行中的场景，需要同时刷新所有相关数据
     * 
     * @param roomCode 房间号
     * @param gameName 游戏名称
     */
    public void refreshRoomTTL(String roomCode, String gameName) {
        // 先刷新房间和玩家信息
        refreshRoomTTL(roomCode);

        // 再刷新游戏数据
        if (gameName != null && !gameName.trim().isEmpty()) {
            String gameKey = gameName + ":" + roomCode;
            if (redisTemplate.hasKey(gameKey)) {
                redisTemplate.expire(gameKey, GAME_TTL_HOURS, TimeUnit.HOURS);
            }
        }
    }

    /**
     * 设置房间初始 TTL
     * 
     * @param roomCode 房间号
     */
    public void setRoomInitialTTL(String roomCode) {
        redisTemplate.expire("room:code:" + roomCode, ROOM_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 设置玩家列表初始 TTL
     * 
     * @param roomCode 房间号
     */
    public void setPlayerInitialTTL(String roomCode) {
        redisTemplate.expire("room:players:" + roomCode, PLAYER_TTL_HOURS, TimeUnit.HOURS);
    }

    /**
     * 设置游戏信息初始 TTL
     * 
     * @param roomCode 房间号
     * @param gameName 游戏名称（如：gomoku, tank-battle, 2048, minesweeper）
     */
    public void setGameInitialTTL(String roomCode, String gameName) {
        if (gameName != null && !gameName.trim().isEmpty()) {
            redisTemplate.expire(gameName + ":" + roomCode, GAME_TTL_HOURS, TimeUnit.HOURS);
        }
    }

    /**
     * 构建游戏数据的 Redis Key
     * 
     * @param roomCode 房间号
     * @param gameName 游戏名称
     * @return Redis Key 字符串
     */
    public String buildGameKey(String roomCode, String gameName) {
        return gameName + ":" + roomCode;
    }

    /**
     * 构建房间信息的 Redis Key
     * 
     * @param roomCode 房间号
     * @return Redis Key 字符串
     */
    public String buildRoomKey(String roomCode) {
        return "room:code:" + roomCode;
    }

    /**
     * 构建房间玩家列表的 Redis Key
     * 
     * @param roomCode 房间号
     * @return Redis Key 字符串
     */
    public String buildPlayerKey(String roomCode) {
        return "room:players:" + roomCode;
    }
}