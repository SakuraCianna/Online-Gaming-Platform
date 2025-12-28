package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.Minesweeper;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface MinesweeperMapper extends BaseMapper<Minesweeper> {
        @Insert("INSERT INTO minesweeper (user_id, difficulty, board_width, board_height, mine_count, correct_flags, duration, status, game_data) "
                        +
                        "VALUES (#{insert.user_id}, #{insert.difficulty}, #{insert.board_width}, #{insert.board_height}, "
                        +
                        "#{insert.mine_count}, #{insert.correct_flags}, #{insert.duration}, #{insert.status}, #{insert.game_data})")
        int insertMinesweeperRecord(@Param("insert") Map<String, Object> insert);

        @Select("SELECT * FROM minesweeper WHERE user_id = #{userId} AND status = 0")
        Minesweeper selectInProgressByUserId(long userId);

        @Update("UPDATE minesweeper SET difficulty = #{update.difficulty}, board_width = #{update.board_width}, " +
                        "board_height = #{update.board_height}, mine_count = #{update.mine_count}, correct_flags = #{update.correct_flags}, "
                        +
                        "duration = #{update.duration}, status = #{update.status}, game_data = #{update.game_data} "
                        +
                        "WHERE id = #{update.id}")
        int updateMinesweeperRecord(@Param("update") Map<String, Object> update);

        // 分页查询用户游戏记录
        @Select("SELECT * FROM minesweeper WHERE user_id = #{userId} " +
                        "ORDER BY create_time DESC LIMIT #{offset}, #{size}")
        List<Minesweeper> selectByUserIdWithPage(@Param("userId") Long userId,
                        @Param("offset") Integer offset,
                        @Param("size") Integer size);

        // 统计用户游戏总数
        @Select("SELECT COUNT(*) FROM minesweeper WHERE user_id = #{userId}")
        Integer countByUserId(@Param("userId") Long userId);

        // 统计用户游戏数据（总数、胜利数、总时长）
        @Select("SELECT COUNT(*) as count, " +
                        "SUM(CASE WHEN status = 1 THEN 1 ELSE 0 END) as wins, " +
                        "SUM(duration) as totalDuration " +
                        "FROM minesweeper WHERE user_id = #{userId}")
        Map<String, Object> selectStatsByUserId(@Param("userId") Long userId);

        // 排行榜：按最快通关时间排序（只统计有胜利记录的用户）
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "MIN(CASE WHEN m.status = 1 THEN m.duration ELSE NULL END) as bestTime, " +
                        "(SELECT COUNT(*) FROM minesweeper WHERE user_id = u.id) as totalGames, " +
                        "ROUND(SUM(CASE WHEN m.status = 1 THEN 1 ELSE 0 END) * 1.0 / COUNT(*), 4) as winRate " +
                        "FROM minesweeper m JOIN `user` u ON m.user_id = u.id " +
                        "GROUP BY u.id HAVING bestTime IS NOT NULL ORDER BY bestTime ASC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByTime(@Param("limit") Integer limit);

        // 排行榜：按胜率排序（至少5局）
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "MIN(CASE WHEN m.status = 1 THEN m.duration ELSE NULL END) as bestTime, " +
                        "COUNT(*) as totalGames, " +
                        "ROUND(SUM(CASE WHEN m.status = 1 THEN 1 ELSE 0 END) * 1.0 / COUNT(*), 4) as winRate " +
                        "FROM minesweeper m JOIN `user` u ON m.user_id = u.id " +
                        "GROUP BY u.id HAVING totalGames >= 5 ORDER BY winRate DESC, totalGames DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByWinRate(@Param("limit") Integer limit);

        // 查询用户排名（按最快时间）
        @Select("SELECT COUNT(*) + 1 FROM (" +
                        "SELECT user_id, MIN(duration) as best FROM minesweeper WHERE status = 1 GROUP BY user_id" +
                        ") t WHERE t.best < (SELECT IFNULL(MIN(duration), 999999) FROM minesweeper WHERE user_id = #{userId} AND status = 1)")
        Integer selectUserRankByTime(@Param("userId") Long userId);

        // 查询用户最快时间
        @Select("SELECT MIN(duration) FROM minesweeper WHERE user_id = #{userId} AND status = 1")
        Integer selectUserBestTime(@Param("userId") Long userId);
}