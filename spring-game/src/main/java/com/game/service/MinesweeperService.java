package com.game.service;

import com.game.exception.BusinessException;
import com.game.mapper.MinesweeperMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.game.entity.Minesweeper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class MinesweeperService {

    private final MinesweeperMapper minesweeperMapper;
    private final GameRecordService gameRecordService;

    public MinesweeperService(MinesweeperMapper minesweeperMapper, GameRecordService gameRecordService) {
        this.minesweeperMapper = minesweeperMapper;
        this.gameRecordService = gameRecordService;
    }

    public Map<String, Object> saveData(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        Long userId = ((Number) request.get("user_id")).longValue();
        String difficulty = (String) request.get("difficulty");
        Integer boardWidth = ((Number) request.get("board_width")).intValue();
        Integer boardHeight = ((Number) request.get("board_height")).intValue();
        Integer mineCount = ((Number) request.get("mine_count")).intValue();
        Integer correctFlags = ((Number) request.get("correct_flags")).intValue();
        Integer duration = ((Number) request.get("duration")).intValue();
        Integer status = ((Number) request.get("status")).intValue();
        String gameData = (String) request.get("game_data");

        Map<String, Object> insert = new HashMap<>();
        insert.put("user_id", userId);
        insert.put("difficulty", difficulty);
        insert.put("board_width", boardWidth);
        insert.put("board_height", boardHeight);
        insert.put("mine_count", mineCount);
        insert.put("correct_flags", correctFlags);
        insert.put("duration", duration);
        insert.put("status", status);
        insert.put("game_data", gameData);

        try {
            if (minesweeperMapper.insertMinesweeperRecord(insert) == 1) {
                result.put("success", true);
                result.put("message", "游戏保存成功");

                // 失效缓存
                gameRecordService.invalidateCache(userId, "minesweeper");
            }
        } catch (Exception e) {
            throw new BusinessException(500, "保存游戏数据失败,请联系管理员");
        }
        return result;
    }

    public Map<String, Object> deleteById(long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (minesweeperMapper.deleteById(id) == 1) {
                result.put("success", true);
                result.put("message", "游戏删除成功");
            }
        } catch (Exception e) {
            throw new BusinessException(500, "游戏删除失败");
        }
        return result;
    }

    public Map<String, Object> updateData(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        // 获取id用于更新
        Long id = ((Number) request.get("id")).longValue();
        String difficulty = (String) request.get("difficulty");
        Integer boardWidth = ((Number) request.get("board_width")).intValue();
        Integer boardHeight = ((Number) request.get("board_height")).intValue();
        Integer mineCount = ((Number) request.get("mine_count")).intValue();
        Integer correctFlags = ((Number) request.get("correct_flags")).intValue();
        Integer duration = ((Number) request.get("duration")).intValue();
        Integer status = ((Number) request.get("status")).intValue();
        String gameData = (String) request.get("game_data");

        Map<String, Object> update = new HashMap<>();
        update.put("id", id);
        update.put("difficulty", difficulty);
        update.put("board_width", boardWidth);
        update.put("board_height", boardHeight);
        update.put("mine_count", mineCount);
        update.put("correct_flags", correctFlags);
        update.put("duration", duration);
        update.put("status", status);
        update.put("game_data", gameData);

        try {
            if (minesweeperMapper.updateMinesweeperRecord(update) == 1) {
                result.put("success", true);
                result.put("message", "游戏更新成功");
            } else {
                result.put("success", false);
                result.put("message", "游戏记录不存在或更新失败");
            }
        } catch (Exception e) {
            throw new BusinessException(500, "更新游戏数据失败,请联系管理员");
        }
        return result;
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
            List<Minesweeper> records = minesweeperMapper.selectByUserIdWithPage(userId, offset, size);

            // 查询总数
            Integer total = minesweeperMapper.countByUserId(userId);

            // 清理敏感数据
            if (records != null) {
                for (Minesweeper record : records) {
                    record.setGameData(null); // 不返回完整游戏数据，节省带宽
                }
            }

            result.put("success", true);
            result.put("records", records);
            result.put("total", total != null ? total : 0);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", total != null ? (int) Math.ceil((double) total / size) : 0);
        } catch (Exception e) {
            throw new BusinessException(500, "查询游戏记录失败：" + e.getMessage());
        }

        return result;
    }
}