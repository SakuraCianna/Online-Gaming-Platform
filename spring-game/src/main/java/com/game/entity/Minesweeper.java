package com.game.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 扫雷游戏记录表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("minesweeper")
public class Minesweeper {

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
     * 难度:beginner-初级,intermediate-中级,expert-高级,custom-自定义
     */
    @TableField("difficulty")
    private String difficulty;

    /**
     * 棋盘宽度
     */
    @TableField("board_width")
    private Integer boardWidth;

    /**
     * 棋盘高度
     */
    @TableField("board_height")
    private Integer boardHeight;

    /**
     * 地雷数量
     */
    @TableField("mine_count")
    private Integer mineCount;

    /**
     * 正确排雷数
     */
    @TableField("correct_flags")
    private Integer correctFlags;

    /**
     * 用时(秒)
     */
    @TableField("duration")
    private Integer duration;

    /**
     * 状态:0-进行中,1-胜利,2-失败
     */
    @TableField("status")
    private Integer status;

    /**
     * 游戏状态数据(JSON格式)
     */
    @TableField("game_data")
    private String gameData;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}