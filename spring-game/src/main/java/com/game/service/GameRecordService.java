package com.game.service;

import com.game.exception.BusinessException;
import com.game.mapper.Game2048Mapper;
import com.game.mapper.GomokuMapper;
import com.game.mapper.MinesweeperMapper;
import com.game.mapper.TankBattleMapper;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
public class GameRecordService {

    private final Game2048Mapper game2048Mapper;
    private final MinesweeperMapper minesweeperMapper;
    private final GomokuMapper gomokuMapper;
    private final TankBattleMapper tankBattleMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RBloomFilter<Long> userIdBloomFilter;

    // 缓存Key前缀
    private static final String STATISTICS_CACHE_PREFIX = "game:statistics:user:";
    private static final String RECORDS_CACHE_PREFIX = "game:";

    // 缓存TTL
    private static final long STATISTICS_CACHE_TTL = 10; // 统计数据10分钟
    private static final long EMPTY_CACHE_TTL = 2; // 空值缓存2分钟

    public GameRecordService(Game2048Mapper game2048Mapper,
                             MinesweeperMapper minesweeperMapper,
                             GomokuMapper gomokuMapper,
                             TankBattleMapper tankBattleMapper,
                             RedisTemplate<String, Object> redisTemplate,
                             RBloomFilter<Long> userIdBloomFilter) {
        this.game2048Mapper = game2048Mapper;
        this.minesweeperMapper = minesweeperMapper;
        this.gomokuMapper = gomokuMapper;
        this.tankBattleMapper = tankBattleMapper;
        this.redisTemplate = redisTemplate;
        this.userIdBloomFilter = userIdBloomFilter;
    }

    // 获取用户游戏统计数据（带缓存和布隆过滤器）
    @Transactional(readOnly = true)
    public Map<String, Object> getStatistics(Long userId) {
        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }

        // 第1层防护：布隆过滤器检查用户是否存在
        if (!userIdBloomFilter.contains(userId)) {
            // 用户不存在，直接返回空统计
            Map<String, Object> emptyResult = new HashMap<>();
            emptyResult.put("total", 0);
            emptyResult.put("totalDuration", 0);
            emptyResult.put("byGame", new HashMap<>());
            return emptyResult;
        }

        // 第2层防护：查询Redis缓存
        String cacheKey = STATISTICS_CACHE_PREFIX + userId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cached;
            return result;
        }

        // 第3层：缓存未命中，从数据库查询
        Map<String, Object> statistics = calculateStatistics(userId);

        // 写入Redis缓存
        if (statistics.get("total").equals(0)) {
            // 空值缓存，TTL较短
            redisTemplate.opsForValue().set(cacheKey, statistics, EMPTY_CACHE_TTL, TimeUnit.MINUTES);
        } else {
            redisTemplate.opsForValue().set(cacheKey, statistics, STATISTICS_CACHE_TTL, TimeUnit.MINUTES);
        }

        return statistics;
    }

    // 计算用户统计数据
    private Map<String, Object> calculateStatistics(Long userId) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Map<String, Object>> byGame = new HashMap<>();

        int totalGames = 0;
        int totalDuration = 0;

        // 统计2048游戏
        Map<String, Object> game2048Stats = game2048Mapper.selectStatsByUserId(userId);
        if (game2048Stats != null) {
            int count = game2048Stats.get("count") != null ? ((Number) game2048Stats.get("count")).intValue() : 0;
            int wins = game2048Stats.get("wins") != null ? ((Number) game2048Stats.get("wins")).intValue() : 0;
            int duration = game2048Stats.get("totalDuration") != null
                    ? ((Number) game2048Stats.get("totalDuration")).intValue()
                    : 0;

            if (count > 0) {
                Map<String, Object> stats = new HashMap<>();
                stats.put("count", count);
                stats.put("wins", wins);
                byGame.put("2048", stats);
                totalGames += count;
                totalDuration += duration;
            }
        }

        // 统计扫雷游戏
        Map<String, Object> minesweeperStats = minesweeperMapper.selectStatsByUserId(userId);
        if (minesweeperStats != null) {
            int count = minesweeperStats.get("count") != null ? ((Number) minesweeperStats.get("count")).intValue() : 0;
            int wins = minesweeperStats.get("wins") != null ? ((Number) minesweeperStats.get("wins")).intValue() : 0;
            int duration = minesweeperStats.get("totalDuration") != null
                    ? ((Number) minesweeperStats.get("totalDuration")).intValue()
                    : 0;

            if (count > 0) {
                Map<String, Object> stats = new HashMap<>();
                stats.put("count", count);
                stats.put("wins", wins);
                byGame.put("minesweeper", stats);
                totalGames += count;
                totalDuration += duration;
            }
        }

        // 统计五子棋游戏
        Map<String, Object> gomokuStats = gomokuMapper.selectStatsByUserId(userId);
        if (gomokuStats != null) {
            int count = gomokuStats.get("count") != null ? ((Number) gomokuStats.get("count")).intValue() : 0;
            int wins = gomokuStats.get("wins") != null ? ((Number) gomokuStats.get("wins")).intValue() : 0;
            int duration = gomokuStats.get("totalDuration") != null
                    ? ((Number) gomokuStats.get("totalDuration")).intValue()
                    : 0;

            if (count > 0) {
                Map<String, Object> stats = new HashMap<>();
                stats.put("count", count);
                stats.put("wins", wins);
                byGame.put("gomoku", stats);
                totalGames += count;
                totalDuration += duration;
            }
        }

        // 统计坦克大战
        Map<String, Object> tankStats = tankBattleMapper.selectStatsByUserId(userId);
        if (tankStats != null) {
            int count = tankStats.get("count") != null ? ((Number) tankStats.get("count")).intValue() : 0;
            int wins = tankStats.get("wins") != null ? ((Number) tankStats.get("wins")).intValue() : 0;
            int duration = tankStats.get("totalDuration") != null ? ((Number) tankStats.get("totalDuration")).intValue()
                    : 0;

            if (count > 0) {
                Map<String, Object> stats = new HashMap<>();
                stats.put("count", count);
                stats.put("wins", wins);
                byGame.put("tank", stats);
                totalGames += count;
                totalDuration += duration;
            }
        }

        result.put("total", totalGames);
        result.put("totalDuration", totalDuration);
        result.put("byGame", byGame);

        return result;
    }

    // 失效缓存（游戏记录保存后调用）
    public void invalidateCache(Long userId, String gameType) {
        if (userId == null || userId <= 0) {
            return;
        }

        try {
            // 删除统计数据缓存
            String statisticsKey = STATISTICS_CACHE_PREFIX + userId;
            redisTemplate.delete(statisticsKey);

            // 删除该游戏类型的首页记录缓存
            if (gameType != null && !gameType.trim().isEmpty()) {
                String recordsKey = RECORDS_CACHE_PREFIX + gameType + ":records:user:" + userId + ":page:1";
                redisTemplate.delete(recordsKey);
            }
        } catch (Exception e) {
            // 缓存失效失败不影响业务，只记录日志
            System.err.println("缓存失效失败：" + e.getMessage());
        }
    }

    // 添加用户ID到布隆过滤器（用户注册时调用）
    public void addUserToBloomFilter(Long userId) {
        if (userId != null && userId > 0) {
            try {
                userIdBloomFilter.add(userId);
            } catch (Exception e) {
                System.err.println("添加用户到布隆过滤器失败：" + e.getMessage());
            }
        }
    }

    // 查询所有游戏类型的记录（混合查询）
    @Transactional(readOnly = true)
    public Map<String, Object> getAllRecords(Long userId, Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();

        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 40; // 默认每页40条
        }

        // 计算偏移量
        int offset = (page - 1) * size;
        int perGameSize = size / 4; // 每个游戏取的数量

        try {
            // 并发查询所有游戏的记录
            java.util.List<Map<String, Object>> allRecords = new java.util.ArrayList<>();

            // 查询2048游戏记录
            java.util.List<com.game.entity.Game2048> game2048Records = game2048Mapper.selectByUserIdWithPage(userId, 0,
                    perGameSize);
            if (game2048Records != null) {
                for (com.game.entity.Game2048 record : game2048Records) {
                    Map<String, Object> recordMap = new HashMap<>();
                    recordMap.put("id", record.getId());
                    recordMap.put("gameType", "2048");
                    recordMap.put("userId", record.getUserId());
                    recordMap.put("difficulty", record.getDifficulty());
                    recordMap.put("maxTile", record.getMaxTile());
                    recordMap.put("movesCount", record.getMovesCount());
                    recordMap.put("duration", record.getDuration());
                    recordMap.put("status", record.getStatus());
                    recordMap.put("score", record.getScore());
                    recordMap.put("createTime", record.getCreateTime());
                    allRecords.add(recordMap);
                }
            }

            // 查询扫雷游戏记录
            java.util.List<com.game.entity.Minesweeper> minesweeperRecords = minesweeperMapper
                    .selectByUserIdWithPage(userId, 0, perGameSize);
            if (minesweeperRecords != null) {
                for (com.game.entity.Minesweeper record : minesweeperRecords) {
                    Map<String, Object> recordMap = new HashMap<>();
                    recordMap.put("id", record.getId());
                    recordMap.put("gameType", "minesweeper");
                    recordMap.put("userId", record.getUserId());
                    recordMap.put("difficulty", record.getDifficulty());
                    recordMap.put("boardWidth", record.getBoardWidth());
                    recordMap.put("boardHeight", record.getBoardHeight());
                    recordMap.put("mineCount", record.getMineCount());
                    recordMap.put("correctFlags", record.getCorrectFlags());
                    recordMap.put("duration", record.getDuration());
                    recordMap.put("status", record.getStatus());
                    recordMap.put("createTime", record.getCreateTime());
                    allRecords.add(recordMap);
                }
            }

            // 查询五子棋游戏记录
            java.util.List<com.game.entity.Gomoku> gomokuRecords = gomokuMapper.selectByUserIdWithPage(userId, 0,
                    perGameSize);
            if (gomokuRecords != null) {
                for (com.game.entity.Gomoku record : gomokuRecords) {
                    Map<String, Object> recordMap = new HashMap<>();
                    recordMap.put("id", record.getId());
                    recordMap.put("gameType", "gomoku");
                    recordMap.put("player1Id", record.getPlayer1Id());
                    recordMap.put("player2Id", record.getPlayer2Id());
                    recordMap.put("winnerId", record.getWinnerId());
                    recordMap.put("loserId", record.getLoserId());
                    recordMap.put("isDraw", record.getIsDraw());
                    recordMap.put("moveCount", record.getMoveCount());
                    recordMap.put("duration", record.getDuration());
                    recordMap.put("startTime", record.getStartTime());
                    recordMap.put("endTime", record.getEndTime());
                    recordMap.put("createTime", record.getCreateTime());
                    allRecords.add(recordMap);
                }
            }

            // 查询坦克大战游戏记录
            java.util.List<com.game.entity.TankBattle> tankRecords = tankBattleMapper.selectByUserIdWithPage(userId, 0,
                    perGameSize);
            if (tankRecords != null) {
                for (com.game.entity.TankBattle record : tankRecords) {
                    Map<String, Object> recordMap = new HashMap<>();
                    recordMap.put("id", record.getId());
                    recordMap.put("gameType", "tank");
                    recordMap.put("userId", record.getUserId());
                    recordMap.put("roomId", record.getRoomId());
                    recordMap.put("duration", record.getDuration());
                    recordMap.put("status", record.getStatus());
                    recordMap.put("startTime", record.getStartTime());
                    recordMap.put("endTime", record.getEndTime());
                    recordMap.put("createTime", record.getCreateTime());
                    allRecords.add(recordMap);
                }
            }

            // 按创建时间倒序排序
            allRecords.sort((a, b) -> {
                java.time.LocalDateTime timeA = (java.time.LocalDateTime) a.getOrDefault("createTime",
                        a.get("startTime"));
                java.time.LocalDateTime timeB = (java.time.LocalDateTime) b.getOrDefault("createTime",
                        b.get("startTime"));
                if (timeA == null)
                    return 1;
                if (timeB == null)
                    return -1;
                return timeB.compareTo(timeA);
            });

            result.put("success", true);
            result.put("message", "查询成功");
            result.put("records", allRecords);
            result.put("total", allRecords.size());

        } catch (Exception e) {
            throw new BusinessException(500, "查询失败：" + e.getMessage());
        }

        return result;
    }
}