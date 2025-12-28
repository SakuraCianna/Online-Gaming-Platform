package com.game.service;

import com.game.exception.BusinessException;
import com.game.mapper.Game2048Mapper;
import com.game.mapper.GomokuMapper;
import com.game.mapper.MinesweeperMapper;
import com.game.mapper.TankBattleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class LeaderboardService {
    private final Game2048Mapper game2048Mapper;
    private final MinesweeperMapper minesweeperMapper;
    private final GomokuMapper gomokuMapper;
    private final TankBattleMapper tankBattleMapper;

    public Map<String, Object> getLeaderboard(String gameType, String rankType, Integer limit, Long userId) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 参数校验：limit最大100，防止恶意请求
            if (limit == null || limit <= 0) limit = 50;
            if (limit > 100) limit = 100;

            List<Map<String, Object>> data;
            Map<String, Object> myRank = null;

            switch (gameType) {
                case "2048" -> {
                    data = get2048Leaderboard(rankType, limit);
                    myRank = get2048UserRank(userId, rankType);
                }
                case "minesweeper" -> {
                    data = getMinesweeperLeaderboard(rankType, limit);
                    myRank = getMinesweeperUserRank(userId, rankType);
                }
                case "gomoku" -> {
                    data = getGomokuLeaderboard(rankType, limit);
                    myRank = getGomokuUserRank(userId, rankType);
                }
                case "tank" -> {
                    data = getTankLeaderboard(rankType, limit);
                    myRank = getTankUserRank(userId, rankType);
                }
                default -> throw new BusinessException(400, "不支持的游戏类型");
            }

            // 处理空数据
            if (data == null) data = new ArrayList<>();

            // 添加排名序号
            for (int i = 0; i < data.size(); i++) {
                data.get(i).put("rank", i + 1);
                // 标记当前用户
                Object userIdObj = data.get(i).get("userId");
                if (userId != null && userIdObj != null && userId.equals(((Number) userIdObj).longValue())) {
                    data.get(i).put("isCurrentUser", true);
                }
            }

            result.put("success", true);
            result.put("data", data);
            result.put("myRank", myRank);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "获取排行榜失败: " + e.getMessage());
        }
        return result;
    }

    // 2048排行榜
    private List<Map<String, Object>> get2048Leaderboard(String rankType, Integer limit) {
        return switch (rankType) {
            case "highScore" -> game2048Mapper.selectLeaderboardByScore(limit);
            case "maxTile" -> game2048Mapper.selectLeaderboardByTile(limit);
            default -> game2048Mapper.selectLeaderboardByScore(limit);
        };
    }

    private Map<String, Object> get2048UserRank(Long userId, String rankType) {
        if (userId == null) return null;
        Integer rank = game2048Mapper.selectUserRankByScore(userId);
        Integer bestScore = game2048Mapper.selectUserBestScore(userId);
        if (bestScore == null) return null;
        Map<String, Object> myRank = new HashMap<>();
        myRank.put("rank", rank);
        myRank.put("bestScore", bestScore);
        return myRank;
    }

    // 扫雷排行榜
    private List<Map<String, Object>> getMinesweeperLeaderboard(String rankType, Integer limit) {
        return switch (rankType) {
            case "fastestTime" -> minesweeperMapper.selectLeaderboardByTime(limit);
            case "winRate" -> minesweeperMapper.selectLeaderboardByWinRate(limit);
            default -> minesweeperMapper.selectLeaderboardByTime(limit);
        };
    }

    private Map<String, Object> getMinesweeperUserRank(Long userId, String rankType) {
        if (userId == null) return null;
        Integer rank = minesweeperMapper.selectUserRankByTime(userId);
        Integer bestTime = minesweeperMapper.selectUserBestTime(userId);
        if (bestTime == null) return null;
        Map<String, Object> myRank = new HashMap<>();
        myRank.put("rank", rank);
        myRank.put("bestTime", bestTime);
        return myRank;
    }

    // 五子棋排行榜
    private List<Map<String, Object>> getGomokuLeaderboard(String rankType, Integer limit) {
        return switch (rankType) {
            case "wins" -> gomokuMapper.selectLeaderboardByWins(limit);
            case "winRate" -> gomokuMapper.selectLeaderboardByWinRate(limit);
            default -> gomokuMapper.selectLeaderboardByWins(limit);
        };
    }

    private Map<String, Object> getGomokuUserRank(Long userId, String rankType) {
        if (userId == null) return null;
        Integer rank = gomokuMapper.selectUserRankByWins(userId);
        Integer wins = gomokuMapper.selectUserWins(userId);
        if (wins == null) return null;
        Map<String, Object> myRank = new HashMap<>();
        myRank.put("rank", rank);
        myRank.put("wins", wins);
        return myRank;
    }

    // 坦克大战排行榜
    private List<Map<String, Object>> getTankLeaderboard(String rankType, Integer limit) {
        return switch (rankType) {
            case "kills" -> tankBattleMapper.selectLeaderboardByKills(limit);
            case "wins" -> tankBattleMapper.selectLeaderboardByWins(limit);
            default -> tankBattleMapper.selectLeaderboardByKills(limit);
        };
    }

    private Map<String, Object> getTankUserRank(Long userId, String rankType) {
        if (userId == null) return null;
        Integer rank = tankBattleMapper.selectUserRankByKills(userId);
        Integer totalKills = tankBattleMapper.selectUserTotalKills(userId);
        if (totalKills == null) return null;
        Map<String, Object> myRank = new HashMap<>();
        myRank.put("rank", rank);
        myRank.put("totalKills", totalKills);
        return myRank;
    }
}
