package com.game.entity;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tank_battle")
public class TankBattle {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long roomId;

    private String mapName;          // 地图名称
    private String gameData;         // JSONB数据（存为String）
    private Integer kills;           // 击杀
    private Integer deaths;          // 死亡
    private Integer assists;         // 助攻
    private Integer damageDealt;     // 造成伤害
    private Integer damageTaken;     // 受到伤害
    private Integer score;           // 得分
    private Integer finalRank;       // 最终排名

    // 原有字段
    private Integer duration;
    private Integer status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}