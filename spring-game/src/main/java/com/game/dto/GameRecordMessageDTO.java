package com.game.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Data
public class GameRecordMessageDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String messageId;   // 消息ID
    private LocalDateTime createTime;   // 创建时间

    private String gameType;   // 游戏类型
    private long userId;    // 用户ID
    private long roomId;    // 房间ID(多人游戏)
    private int duration;   // 游戏时长
    private int status;     // 状态:0-进行中, 1-胜利/通关/已结束, 2-失败
    private String gameData;    // 游戏数据

    private Map<String, Object> gameExtraData;  // 游戏额外数据

    public static GameRecordMessageDTO build2048(Long userId, String difficulty, Integer maxTile,
                                              Integer movesCount, Integer duration, Integer status,
                                              String gameData, Integer score) {
        GameRecordMessageDTO message = new GameRecordMessageDTO();
        message.setMessageId(java.util.UUID.randomUUID().toString());
        message.setCreateTime(LocalDateTime.now());
        message.setGameType("2048");
        message.setUserId(userId);
        message.setDuration(duration);
        message.setStatus(status);
        message.setGameData(gameData);

        // 额外字段
        Map<String, Object> specificData = new java.util.HashMap<>();
        specificData.put("difficulty", difficulty);
        specificData.put("max_tile", maxTile);
        specificData.put("moves_count", movesCount);
        specificData.put("score", score);
        message.setGameExtraData(specificData);

        return message;
    }

    public static GameRecordMessageDTO buildGomoku(Long roomId, Long player1Id, Long player2Id,
                                                Long winnerId, Long loserId, Integer isDraw,
                                                Integer moveCount, Integer duration,
                                                String gameData) {
        GameRecordMessageDTO message = new GameRecordMessageDTO();
        message.setMessageId(java.util.UUID.randomUUID().toString());
        message.setCreateTime(LocalDateTime.now());
        message.setGameType("gomoku");
        message.setRoomId(roomId);
        message.setUserId(player1Id); // 主要用户设为player1
        message.setDuration(duration);
        message.setStatus(1); // 五子棋结束时才保存，所以都是已结束状态
        message.setGameData(gameData);

        Map<String, Object> specificData = new java.util.HashMap<>();
        specificData.put("player1_id", player1Id);
        specificData.put("player2_id", player2Id);
        specificData.put("winner_id", winnerId);
        specificData.put("loser_id", loserId);
        specificData.put("is_draw", isDraw);
        specificData.put("move_count", moveCount);
        message.setGameExtraData(specificData);

        return message;
    }

    public static GameRecordMessageDTO buildMinesweeper(Long userId, String difficulty,
                                                     Integer boardWidth, Integer boardHeight,
                                                     Integer mineCount, Integer correctFlags,
                                                     Integer duration, Integer status,
                                                     String gameData) {
        GameRecordMessageDTO message = new GameRecordMessageDTO();
        message.setMessageId(java.util.UUID.randomUUID().toString());
        message.setCreateTime(LocalDateTime.now());
        message.setGameType("minesweeper");
        message.setUserId(userId);
        message.setDuration(duration);
        message.setStatus(status);
        message.setGameData(gameData);

        Map<String, Object> specificData = new java.util.HashMap<>();
        specificData.put("difficulty", difficulty);
        specificData.put("board_width", boardWidth);
        specificData.put("board_height", boardHeight);
        specificData.put("mine_count", mineCount);
        specificData.put("correct_flags", correctFlags);
        message.setGameExtraData(specificData);

        return message;
    }

}
