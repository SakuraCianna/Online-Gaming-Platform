package com.game.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 房间玩家表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("room_player")
public class RoomPlayer {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 房间ID
     */
    @TableField("room_id")
    private Long roomId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 是否AI玩家:1-是,0-否
     */
    @TableField("is_ai")
    private Integer isAi;

    /**
     * AI类型:simple-简单,advanced-高级,custom-自定义
     */
    @TableField("ai_type")
    private String aiType;

    /**
     * AI配置参数(JSON格式)
     */
    @TableField(value = "ai_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> aiConfig;

    /**
     * 队伍ID:0-无队伍,1-队伍A,2-队伍B,依此类推
     */
    @TableField("team_id")
    private Integer teamId;

    /**
     * 是否准备:1-已准备,0-未准备
     */
    @TableField("is_ready")
    private Integer isReady;

    /**
     * 玩家角色:owner-房主,captain-队长,member-队员
     */
    @TableField("player_role")
    private String playerRole;

    /**
     * 位置编号:用于记录玩家座位/顺序
     */
    @TableField("position")
    private Integer position;

    /**
     * 击杀数
     */
    @TableField("`kill`")
    private Integer kill;

    /**
     * 死亡次数
     */
    @TableField("`death`")
    private Integer death;

    /**
     * 协助数
     */
    @TableField("`assist`")
    private Integer assist;

    /**
     * 加入时间
     */
    @TableField("join_time")
    private LocalDateTime joinTime;

    /**
     * 离开时间
     */
    @TableField("leave_time")
    private LocalDateTime leaveTime;
}