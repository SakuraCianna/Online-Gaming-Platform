package com.game.service;

import com.game.exception.BusinessException;
import com.game.mapper.Game2048Mapper;
import com.game.mapper.GomokuMapper;
import com.game.mapper.MinesweeperMapper;
import com.game.mapper.TankBattleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
@RequiredArgsConstructor
public class LeaderboardService {
    private final Game2048Mapper game2048Mapper;
    private final MinesweeperMapper minesweeperMapper;
    private final GomokuMapper gomokuMapper;
    private final TankBattleMapper tankBattleMapper;

    public Map<String, Object> getLeaderboard(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "计算失败: " + e.getMessage());
        }
        return result;
    }
}
