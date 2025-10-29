package com.game.controller;

import com.game.service.LeaderboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {
    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getLeaderboard(@RequestBody Map<String,Object> request) {
        Map<String, Object> result = leaderboardService.getLeaderboard(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

}
