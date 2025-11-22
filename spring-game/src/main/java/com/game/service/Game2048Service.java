package com.game.service;

import com.game.config.RabbitMQConfig;
import com.game.dto.GameRecordMessageDTO;
import com.game.exception.BusinessException;
import com.game.mapper.Game2048Mapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.game.entity.Game2048;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class Game2048Service {
    private final Game2048Mapper game2048Mapper;
    private final RabbitTemplate rabbitTemplate;

    public Map<String, Object> saveData(Map<String, Object> request) {
        log.info("开始保存2048游戏数据: userId={}", request.get("user_id"));

        // 参数提取和校验
        if (request.get("user_id") == null) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        Long userId = ((Number) request.get("user_id")).longValue();
        String difficulty = (String) request.get("difficulty");
        Integer maxTile = ((Number) request.get("max_tile")).intValue();
        Integer movesCount = ((Number) request.get("moves_count")).intValue();
        Integer duration = ((Number) request.get("duration")).intValue();
        int status = ((Number) request.get("status")).intValue();
        Integer score = ((Number) request.get("score")).intValue();
        String gameData = (String) request.get("game_data");

        // 参数校验
        if (difficulty == null || difficulty.trim().isEmpty()) {
            throw new BusinessException(400, "难度不能为空");
        }
        if (gameData == null || gameData.trim().isEmpty()) {
            throw new BusinessException(400, "游戏数据不能为空");
        }
        if (gameData.length() > 1_000_000) {
            throw new BusinessException(400, "游戏数据过大");
        }

        Map<String, Object> result = new HashMap<>();

        try {
            // 构建消息并发送到RabbitMQ队列
            GameRecordMessageDTO message = GameRecordMessageDTO.build2048(
                    userId, difficulty, maxTile, movesCount,
                    duration, status, gameData, score
            );

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.GAME_RECORD_EXCHANGE,
                    RabbitMQConfig.GAME_RECORD_ROUTING_KEY,
                    message
            );

            result.put("success", true);
            result.put("message", "游戏保存请求已提交");
            log.info("游戏保存消息已发送到RabbitMQ: userId={}, status={}", userId, status);
        } catch (Exception e) {
            log.error("发送游戏保存消息失败: userId={}, 错误: {}", userId, e.getMessage(), e);
            throw new BusinessException(500, "保存游戏数据失败: " + e.getMessage());
        }

        return result;
    }


    public Map<String, Object> deleteById(long id, Long userId) {
        log.info("开始删除2048游戏记录: id={}, userId={}", id, userId);
        Map<String, Object> result = new HashMap<>();

        try {
            // 先查询记录是否存在
            Game2048 existing = game2048Mapper.selectById(id);
            if (existing == null) {
                throw new BusinessException(404, "游戏记录不存在");
            }

            // 验证是否是该用户的记录（如果提供了userId）
            if (userId != null && !existing.getUserId().equals(userId)) {
                throw new BusinessException(403, "无权限删除该游戏记录");
            }

            if (game2048Mapper.deleteById(id) == 1) {
                result.put("success", true);
                result.put("message", "游戏删除成功");
                log.info("2048游戏删除成功: id={}, userId={}", id, userId);
            } else {
                result.put("success", false);
                result.put("message", "游戏删除失败");
                log.warn("2048游戏删除失败(数据库返回0): id={}", id);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除2048游戏失败: id={}, 错误信息: {}", id, e.getMessage(), e);
            throw new BusinessException(500, "游戏删除失败: " + e.getMessage());
        }
        return result;
    }


    /**
     * 获取用户进行中的游戏
     *
     * @param userId 用户ID
     * @return 进行中的游戏记录，没有则返回null
     */
    @Transactional(readOnly = true)
    public Game2048 getInProgressGame(Long userId) {
        log.info("查询用户进行中的2048游戏: userId={}", userId);

        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }

        try {
            Game2048 inProgress = game2048Mapper.selectInProgressByUserId(userId);
            if (inProgress != null) {
                log.info("找到进行中的2048游戏: userId={}, gameId={}, difficulty={}, score={}",
                        userId, inProgress.getId(), inProgress.getDifficulty(), inProgress.getScore());
            } else {
                log.info("用户没有进行中的2048游戏: userId={}", userId);
            }
            return inProgress;
        } catch (Exception e) {
            log.error("查询进行中的2048游戏失败: userId={}, 错误信息: {}", userId, e.getMessage(), e);
            throw new BusinessException(500, "查询进行中的游戏失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询用户游戏记录
     * 
     * @param userId 用户ID
     * @param page   页码（从1开始）
     * @param size   每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRecordsByUserId(Long userId, Integer page, Integer size) {
        log.info("查询2048游戏记录: userId={}, page={}, size={}", userId, page, size);
        Map<String, Object> result = new HashMap<>();

        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 20; // 默认每页20条
        }

        try {
            // 计算偏移量
            int offset = (page - 1) * size;

            // 查询记录
            List<Game2048> records = game2048Mapper.selectByUserIdWithPage(userId, offset, size);

            // 查询总数
            Integer total = game2048Mapper.countByUserId(userId);

            // 清理敏感数据
            if (records != null) {
                for (Game2048 record : records) {
                    record.setGameData(null); // 不返回完整游戏数据，节省带宽
                }
            }

            result.put("success", true);
            result.put("records", records);
            result.put("total", total != null ? total : 0);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", total != null ? (int) Math.ceil((double) total / size) : 0);
            log.info("2048游戏记录查询成功: userId={}, 共{}条记录", userId, total);
        } catch (Exception e) {
            log.error("查询2048游戏记录失败: userId={}, 错误信息: {}", userId, e.getMessage(), e);
            throw new BusinessException(500, "查询游戏记录失败: " + e.getMessage());
        }

        return result;
    }
}
