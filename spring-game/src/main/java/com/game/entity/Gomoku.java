package com.game.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.game.vo.PointVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 五子棋对局记录表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("gomoku_game")
public class Gomoku {

    /** 对局ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 房间ID */
    @TableField("room_id")
    private Long roomId;

    /** 玩家1用户ID(先手) */
    @TableField("player1_id")
    private Long player1Id;

    /** 玩家2用户ID(后手) */
    @TableField("player2_id")
    private Long player2Id;

    /** 胜者用户ID,平局为NULL */
    @TableField("winner_id")
    private Long winnerId;

    /** 败者用户ID,平局为NULL */
    @TableField("loser_id")
    private Long loserId;

    /** 是否平局: 1-平局, 0-非平局 */
    @TableField("is_draw")
    private Integer isDraw;

    /** 总步数 */
    @TableField("move_count")
    private Integer moveCount;

    /** 用时(秒) */
    @TableField("duration")
    private Integer duration;

    /** 开始时间 */
    @TableField("start_time")
    private LocalDateTime startTime;

    /** 结束时间 */
    @TableField("end_time")
    private LocalDateTime endTime;

    /** 最终棋盘状态或回放(JSON) */
    @TableField(value = "game_data", typeHandler = JacksonTypeHandler.class)
    private List<PointVO> gameData;

    /** 创建时间 */
    @TableField("create_time")
    private LocalDateTime createTime;
}