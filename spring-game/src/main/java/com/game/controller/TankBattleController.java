package com.game.controller;

import com.game.component.JwtUtil;
import com.game.service.TankBattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tankbattle")
@RequiredArgsConstructor
public class TankBattleController {
    private final TankBattleService tankBattleService;

    @PostMapping("/{roomCode}/start")
    public ResponseEntity<Map<String, Object>> startGame(@PathVariable String roomCode, @RequestBody Map<String, Object> request) {
        Long currentUserId = JwtUtil.getCurrentUserId();
        Map<String, Object> result = tankBattleService.startGame(roomCode, request, currentUserId);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}
