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

        // 排行榜：按最高分排序
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "MAX(g.score) as bestScore, MAX(g.max_tile) as bestTile, COUNT(*) as totalGames " +
                        "FROM game2048 g JOIN `user` u ON g.user_id = u.id " +
                        "WHERE g.status = 1 " +
                        "GROUP BY u.id ORDER BY bestScore DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByScore(@Param("limit") Integer limit);

        // 排行榜：按最大方块排序
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "MAX(g.score) as bestScore, MAX(g.max_tile) as bestTile, COUNT(*) as totalGames " +
                        "FROM game2048 g JOIN `user` u ON g.user_id = u.id " +
                        "WHERE g.status = 1 " +
                        "GROUP BY u.id ORDER BY bestTile DESC, bestScore DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByTile(@Param("limit") Integer limit);

        // 查询用户排名（按最高分）
        @Select("SELECT COUNT(*) + 1 FROM (" +
                        "SELECT user_id, MAX(score) as best FROM game2048 WHERE status = 1 GROUP BY user_id" +
                        ") t WHERE t.best > (SELECT IFNULL(MAX(score), 0) FROM game2048 WHERE user_id = #{userId} AND status = 1)")
        Integer selectUserRankByScore(@Param("userId") Long userId);

        // 查询用户最高分
        @Select("SELECT MAX(score) FROM game2048 WHERE user_id = #{userId} AND status = 1")
        Integer selectUserBestScore(@Param("userId") Long userId);
}
