package com.game.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 游戏房间表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("game_room")
public class GameRoom {

    /**
     * 房间ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 房间码
     */
    @TableField("room_code")
    private String roomCode;

    /**
     * 游戏名称
     */
    @TableField("game_name")
    private String gameName;

    /**
     * 当前玩家数
     */
    @TableField("players")
    private Integer players;

    /**
     * 最大玩家数
     */
    @TableField("max_players")
    private Integer maxPlayers;

    /**
     * 最小玩家数
     */
    @TableField("min_players")
    private Integer minPlayers;

    /**
     * 状态:0-等待中,1-游戏中,2-已结束
     */
    @TableField("status")
    private Integer status;

    /**
     * 队伍模式:0-无队伍,1-1v1,2-2v2,3-3v3,4-自由队伍
     */
    @TableField("team_mode")
    private Integer teamMode;

    /**
     * 游戏配置(JSON格式)
     */
    @TableField("game_config")
    private String gameConfig;

    /**
     * 胜利者ID
     */
    @TableField("winner_id")
    private Long winnerId;

    /**
     * 创建者ID
     */
    @TableField("creator_id")
    private Long creatorId;

    /**
     * 是否私密房间:1-是,0-否
     */
    @TableField("is_private")
    private Integer isPrivate;

    /**
     * 开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @TableField("end_time")
    private LocalDateTime endTime;
}