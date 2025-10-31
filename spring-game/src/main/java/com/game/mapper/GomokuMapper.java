package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.Gomoku;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface GomokuMapper extends BaseMapper<Gomoku> {

        // 分页查询用户游戏记录
        @Select("SELECT * FROM gomoku_game WHERE player1_id = #{userId} OR player2_id = #{userId} " +
                        "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
        List<Gomoku> selectByUserIdWithPage(@Param("userId") Long userId,
                        @Param("offset") Integer offset,
                        @Param("size") Integer size);

        // 统计用户游戏总数
        @Select("SELECT COUNT(*) FROM gomoku_game WHERE player1_id = #{userId} OR player2_id = #{userId}")
        Integer countByUserId(@Param("userId") Long userId);

        // 统计用户游戏数据（总数、胜利数、总时长）
        @Select("SELECT COUNT(*) as count, " +
                        "SUM(CASE WHEN winner_id = #{userId} THEN 1 ELSE 0 END) as wins, " +
                        "SUM(duration) as totalDuration " +
                        "FROM gomoku_game WHERE player1_id = #{userId} OR player2_id = #{userId}")
        Map<String, Object> selectStatsByUserId(@Param("userId") Long userId);
}
