package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.TankBattle;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface TankBattleMapper extends BaseMapper<TankBattle> {

        // 分页查询用户游戏记录
        @Select("SELECT * FROM tank_battle WHERE user_id = #{userId} " +
                        "ORDER BY start_time DESC LIMIT #{size} OFFSET #{offset}")
        List<TankBattle> selectByUserIdWithPage(@Param("userId") Long userId,
                        @Param("offset") Integer offset,
                        @Param("size") Integer size);

        // 统计用户游戏总数
        @Select("SELECT COUNT(*) FROM tank_battle WHERE user_id = #{userId}")
        Integer countByUserId(@Param("userId") Long userId);

        // 统计用户游戏数据（总数、总时长）
        @Select("SELECT COUNT(*) as count, " +
                        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as wins, " +
                        "SUM(duration) as totalDuration " +
                        "FROM tank_battle WHERE user_id = #{userId}")
        Map<String, Object> selectStatsByUserId(@Param("userId") Long userId);
}
