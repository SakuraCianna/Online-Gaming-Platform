package com.game.controller;

import com.game.service.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/record")
public class GameRecordController {

    private final GameRecordService gameRecordService;
    private final Game2048Service game2048Service;
    private final MinesweeperService minesweeperService;
    private final GomokuService gomokuService;
    private final TankBattleService tankBattleService;
    private final RedisTemplate<String, Object> redisTemplate;

    // 缓存Key前缀
    private static final String RECORDS_CACHE_PREFIX = "game:";
    private static final long RECORDS_CACHE_TTL = 5; // 记录缓存5分钟

    public GameRecordController(GameRecordService gameRecordService,
            Game2048Service game2048Service,
            MinesweeperService minesweeperService,
            GomokuService gomokuService,
            TankBattleService tankBattleService,
            RedisTemplate<String, Object> redisTemplate) {
        this.gameRecordService = gameRecordService;
        this.game2048Service = game2048Service;
        this.minesweeperService = minesweeperService;
        this.gomokuService = gomokuService;
        this.tankBattleService = tankBattleService;
        this.redisTemplate = redisTemplate;
    }

    // 获取用户游戏统计数据
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getStatistics(@RequestParam Long userId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Map<String, Object> statistics = gameRecordService.getStatistics(userId);
            result.put("code", 200);
            result.put("success", true);
            result.put("message", "查询成功");
            result.put("data", statistics);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(result);
    }

    // 查询所有游戏类型的记录（混合查询）
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllRecords(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "40") Integer size) {

        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> result = gameRecordService.getAllRecords(userId, page, size);
            response.put("code", 200);
            response.putAll(result);
        } catch (Exception e) {
            response.put("code", 500);
            response.put("success", false);
            response.put("message", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // 分页查询2048游戏记录
    @SuppressWarnings("unchecked")
    @GetMapping("/game2048")
    public ResponseEntity<Map<String, Object>> get2048Records(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 仅首页查询缓存
            if (page == 1) {
                String cacheKey = RECORDS_CACHE_PREFIX + "2048:records:user:" + userId + ":page:1";
                Object cached = redisTemplate.opsForValue().get(cacheKey);

                if (cached != null) {
                    response.put("code", 200);
                    response.putAll((Map<String, Object>) cached);
                    return ResponseEntity.ok(response);
                }

                // 缓存未命中，查询数据库
                Map<String, Object> result = game2048Service.getRecordsByUserId(userId, page, size);

                // 写入缓存
                redisTemplate.opsForValue().set(cacheKey, result, RECORDS_CACHE_TTL, TimeUnit.MINUTES);

                response.put("code", 200);
                response.putAll(result);
            } else {
                // 非首页不缓存
                Map<String, Object> result = game2048Service.getRecordsByUserId(userId, page, size);
                response.put("code", 200);
                response.putAll(result);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("success", false);
            response.put("message", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // 分页查询扫雷游戏记录
    @GetMapping("/minesweeper")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getMinesweeperRecords(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 仅首页查询缓存
            if (page == 1) {
                String cacheKey = RECORDS_CACHE_PREFIX + "minesweeper:records:user:" + userId + ":page:1";
                Object cached = redisTemplate.opsForValue().get(cacheKey);

                if (cached != null) {
                    response.put("code", 200);
                    response.putAll((Map<String, Object>) cached);
                    return ResponseEntity.ok(response);
                }

                // 缓存未命中，查询数据库
                Map<String, Object> result = minesweeperService.getRecordsByUserId(userId, page, size);

                // 写入缓存
                redisTemplate.opsForValue().set(cacheKey, result, RECORDS_CACHE_TTL, TimeUnit.MINUTES);

                response.put("code", 200);
                response.putAll(result);
            } else {
                // 非首页不缓存
                Map<String, Object> result = minesweeperService.getRecordsByUserId(userId, page, size);
                response.put("code", 200);
                response.putAll(result);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("success", false);
            response.put("message", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // 分页查询五子棋游戏记录
    @GetMapping("/gomoku")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getGomokuRecords(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 仅首页查询缓存
            if (page == 1) {
                String cacheKey = RECORDS_CACHE_PREFIX + "gomoku:records:user:" + userId + ":page:1";
                Object cached = redisTemplate.opsForValue().get(cacheKey);

                if (cached != null) {
                    response.put("code", 200);
                    response.putAll((Map<String, Object>) cached);
                    return ResponseEntity.ok(response);
                }

                // 缓存未命中，查询数据库
                Map<String, Object> result = gomokuService.getRecordsByUserId(userId, page, size);

                // 写入缓存
                redisTemplate.opsForValue().set(cacheKey, result, RECORDS_CACHE_TTL, TimeUnit.MINUTES);

                response.put("code", 200);
                response.putAll(result);
            } else {
                // 非首页不缓存
                Map<String, Object> result = gomokuService.getRecordsByUserId(userId, page, size);
                response.put("code", 200);
                response.putAll(result);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("success", false);
            response.put("message", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // 分页查询坦克大战游戏记录
    @GetMapping("/tank")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> getTankRecords(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 仅首页查询缓存
            if (page == 1) {
                String cacheKey = RECORDS_CACHE_PREFIX + "tank:records:user:" + userId + ":page:1";
                Object cached = redisTemplate.opsForValue().get(cacheKey);

                if (cached != null) {
                    response.put("code", 200);
                    response.putAll((Map<String, Object>) cached);
                    return ResponseEntity.ok(response);
                }

                // 缓存未命中，查询数据库
                Map<String, Object> result = tankBattleService.getRecordsByUserId(userId, page, size);

                // 写入缓存
                redisTemplate.opsForValue().set(cacheKey, result, RECORDS_CACHE_TTL, TimeUnit.MINUTES);

                response.put("code", 200);
                response.putAll(result);
            } else {
                // 非首页不缓存
                Map<String, Object> result = tankBattleService.getRecordsByUserId(userId, page, size);
                response.put("code", 200);
                response.putAll(result);
            }
        } catch (Exception e) {
            response.put("code", 500);
            response.put("success", false);
            response.put("message", "查询失败：" + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }
}
