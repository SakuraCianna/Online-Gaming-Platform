package com.game.service;

import com.game.entity.TankBattle;
import com.game.exception.BusinessException;
import com.game.mapper.TankBattleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class TankBattleService {

    private final TankBattleMapper tankBattleMapper;

    public TankBattleService(TankBattleMapper tankBattleMapper) {
        this.tankBattleMapper = tankBattleMapper;
    }

    // 分页查询用户游戏记录
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
            List<TankBattle> records = tankBattleMapper.selectByUserIdWithPage(userId, offset, size);

            // 查询总数
            Integer total = tankBattleMapper.countByUserId(userId);

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