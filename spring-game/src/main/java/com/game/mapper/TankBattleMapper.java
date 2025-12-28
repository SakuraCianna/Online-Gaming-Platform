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
                        "ORDER BY start_time DESC LIMIT #{offset}, #{size}")
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

        // 排行榜：按总击杀数排序
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "SUM(t.kills) as totalKills, " +
                        "SUM(CASE WHEN t.status = 1 THEN 1 ELSE 0 END) as wins, " +
                        "COUNT(*) as totalGames " +
                        "FROM tank_battle t JOIN `user` u ON t.user_id = u.id " +
                        "GROUP BY u.id ORDER BY totalKills DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByKills(@Param("limit") Integer limit);

        // 排行榜：按胜场数排序
        @Select("SELECT u.id as userId, u.username, u.avatar, " +
                        "SUM(t.kills) as totalKills, " +
                        "SUM(CASE WHEN t.status = 1 THEN 1 ELSE 0 END) as wins, " +
                        "COUNT(*) as totalGames " +
                        "FROM tank_battle t JOIN `user` u ON t.user_id = u.id " +
                        "GROUP BY u.id ORDER BY wins DESC, totalKills DESC LIMIT #{limit}")
        List<Map<String, Object>> selectLeaderboardByWins(@Param("limit") Integer limit);

        // 查询用户排名（按总击杀）
        @Select("SELECT COUNT(*) + 1 FROM (" +
                        "SELECT user_id, SUM(kills) as total FROM tank_battle GROUP BY user_id" +
                        ") t WHERE t.total > (SELECT IFNULL(SUM(kills), 0) FROM tank_battle WHERE user_id = #{userId})")
        Integer selectUserRankByKills(@Param("userId") Long userId);

        // 查询用户总击杀
        @Select("SELECT SUM(kills) FROM tank_battle WHERE user_id = #{userId}")
        Integer selectUserTotalKills(@Param("userId") Long userId);
}