package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.Game2048;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface Game2048Mapper extends BaseMapper<Game2048> {
        @Select("SELECT * FROM game2048 WHERE user_id = #{userId} AND status = 0")
        Game2048 selectInProgressByUserId(long userId);

        // 分页查询用户游戏记录
        @Select("SELECT * FROM game2048 WHERE user_id = #{userId} " +
                        "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
        List<Game2048> selectByUserIdWithPage(@Param("userId") Long userId,
                        @Param("offset") Integer offset,
                        @Param("size") Integer size);

        // 统计用户游戏总数
        @Select("SELECT COUNT(*) FROM game2048 WHERE user_id = #{userId}")
        Integer countByUserId(@Param("userId") Long userId);

        // 统计用户游戏数据（总数、胜利数、总时长）
        @Select("SELECT COUNT(*) as count, " +
                        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as wins, " +
                        "SUM(duration) as totalDuration " +
                        "FROM game2048 WHERE user_id = #{userId}")
        Map<String, Object> selectStatsByUserId(@Param("userId") Long userId);
}
