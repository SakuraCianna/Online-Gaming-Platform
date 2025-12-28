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

        // 排行榜：按胜场数排序
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "SUM(CASE WHEN g.winner_id = u.id THEN 1 ELSE 0 END) as wins, " +
                        "COUNT(*) as totalGames, " +
                        "ROUND(SUM(CASE WHEN g.winner_id = u.id THEN 1 ELSE 0 END) * 1.0 / COUNT(*), 4) as winRate " +
                        "FROM gomoku_game g JOIN `user` u ON (g.player1_id = u.id OR g.player2_id = u.id) " +
                        "GROUP BY u.id ORDER BY wins DESC, winRate DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByWins(@Param("limit") Integer limit);

        // 排行榜：按胜率排序（至少5局）
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "SUM(CASE WHEN g.winner_id = u.id THEN 1 ELSE 0 END) as wins, " +
                        "COUNT(*) as totalGames, " +
                        "ROUND(SUM(CASE WHEN g.winner_id = u.id THEN 1 ELSE 0 END) * 1.0 / COUNT(*), 4) as winRate " +
                        "FROM gomoku_game g JOIN `user` u ON (g.player1_id = u.id OR g.player2_id = u.id) " +
                        "GROUP BY u.id HAVING totalGames >= 5 ORDER BY winRate DESC, wins DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByWinRate(@Param("limit") Integer limit);

        // 查询用户排名（按胜场）
        @Select("SELECT COUNT(*) + 1 FROM (" +
                        "SELECT u.id, SUM(CASE WHEN g.winner_id = u.id THEN 1 ELSE 0 END) as wins " +
                        "FROM gomoku_game g JOIN `user` u ON (g.player1_id = u.id OR g.player2_id = u.id) " +
                        "GROUP BY u.id) t WHERE t.wins > (" +
                        "SELECT IFNULL(SUM(CASE WHEN winner_id = #{userId} THEN 1 ELSE 0 END), 0) " +
                        "FROM gomoku_game WHERE player1_id = #{userId} OR player2_id = #{userId})")
        Integer selectUserRankByWins(@Param("userId") Long userId);

        // 查询用户胜场数
        @Select("SELECT SUM(CASE WHEN winner_id = #{userId} THEN 1 ELSE 0 END) " +
                        "FROM gomoku_game WHERE player1_id = #{userId} OR player2_id = #{userId}")
        Integer selectUserWins(@Param("userId") Long userId);
}
