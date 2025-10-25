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
                        "duration = #{update.duration}, status = #{update.status}, game_data = #{update.game_data} " +
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
}