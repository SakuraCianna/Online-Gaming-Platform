package com.game.controller;

import com.game.component.JwtUtil;
import com.game.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {
    private final LeaderboardService leaderboardService;

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getLeaderboard(
            @RequestParam String gameType,
            @RequestParam String rankType,
            @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = null;
        try {
            userId = JwtUtil.getCurrentUserId();
        } catch (Exception ignored) {
            // 未登录用户，userId为null
        }
        Map<String, Object> result = leaderboardService.getLeaderboard(gameType, rankType, limit, userId);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}
