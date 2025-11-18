package com.game.controller;

import com.game.service.TankBattleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tankbattle")
public class TankBattleController {
    private final TankBattleService tankBattleService;

    public TankBattleController(TankBattleService tankBattleService) {
        this.tankBattleService = tankBattleService;
    }

    @PostMapping("/{roomCode}/start")
    public ResponseEntity<Map<String, Object>> startGame(@PathVariable String roomCode, @RequestBody Map<String, Object> request) {
        Map<String, Object> result = tankBattleService.startGame(roomCode, request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}
