package com.game.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;
import java.util.Collections;
import java.util.List;

/**
 * 游戏结束事件
 * 支持单人和多人游戏
 */
@Getter
public class GameEndEvent extends ApplicationEvent {

    private final Long userId; // 主玩家ID
    private final String gameType; // 游戏类型（gomoku, 2048, minesweeper, tank等）
    private final List<Long> playerIds; // 所有玩家ID列表（包括主玩家）

    /**
     * 多人游戏构造器（推荐使用）
     * 
     * @param source    事件源（通常是发布事件的Service）
     * @param gameType  游戏类型
     * @param playerIds 所有玩家ID列表
     */
    public GameEndEvent(Object source, String gameType, List<Long> playerIds) {
        super(source);
        this.userId = playerIds != null && !playerIds.isEmpty() ? playerIds.getFirst() : null;
        this.gameType = gameType;
        this.playerIds = playerIds != null ? List.copyOf(playerIds) : Collections.emptyList();
    }

    /**
     * 单人游戏构造器
     * 
     * @param source   事件源
     * @param userId   玩家ID
     * @param gameType 游戏类型
     */
    public GameEndEvent(Object source, Long userId, String gameType) {
        this(source, gameType, List.of(userId));
    }

    /**
     * 双人游戏构造器（兼容原有代码）
     * 
     * @param source    事件源
     * @param player1Id 玩家1 ID
     * @param gameType  游戏类型
     * @param player2Id 玩家2 ID（可为null）
     */
    public GameEndEvent(Object source, Long player1Id, String gameType, Long player2Id) {
        this(source, gameType, player2Id != null && player2Id > 0
                ? List.of(player1Id, player2Id)
                : List.of(player1Id));
    }
}
