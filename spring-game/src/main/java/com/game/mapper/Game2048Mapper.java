package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.Game2048;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface Game2048Mapper extends BaseMapper<Game2048> {
        @Insert("INSERT INTO game2048 (user_id, difficulty, max_tile, moves_count, duration, status, game_data, score) "
                        +
                        "VALUES (#{insert.user_id}, #{insert.difficulty}, #{insert.max_tile}, #{insert.moves_count}, " +
                        "#{insert.duration}, #{insert.status}, CAST(#{insert.game_data} AS jsonb), #{insert.score})")
        int insert2048Record(@Param("insert") Map<String, Object> insert);

        @Select("SELECT * FROM game2048 WHERE user_id = #{userId} AND status = 0")
        Game2048 selectInProgressByUserId(long userId);

        @Update("UPDATE game2048 SET difficulty = #{update.difficulty}, max_tile = #{update.max_tile}, " +
                        "moves_count = #{update.moves_count}, duration = #{update.duration}, status = #{update.status}, "
                        +
                        "game_data = CAST(#{update.game_data} AS jsonb), score = #{update.score} " +
                        "WHERE id = #{update.id}")
        int update2048Record(@Param("update") Map<String, Object> update);

        // 分页查询用户游戏记录
        @Select("SELECT * FROM game2048 WHERE user_id = #{userId} " +
                        "ORDER BY create_time DESC LIMIT #{size} OFFSET #{offset}")
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
