package com.game.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 2048游戏记录表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("game2048")
public class Game2048 {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 难度:easy-简单,normal-普通,hard-困难
     */
    @TableField("difficulty")
    private String difficulty;

    /**
     * 最大方块数值
     */
    @TableField("max_tile")
    private Integer maxTile;

    /**
     * 移动步数
     */
    @TableField("moves_count")
    private Integer movesCount;

    /**
     * 游戏时长(秒)
     */
    @TableField("duration")
    private Integer duration;

    /**
     * 状态:0-进行中,1-已结束,2-已取消
     */
    @TableField("status")
    private Integer status;

    /**
     * 游戏状态数据(JSON格式)
     */
    @TableField("game_data")
    private String gameData;

    /**
     * 得分
     */
    @TableField("score")
    private Integer score;

    /**
     * 开始时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}