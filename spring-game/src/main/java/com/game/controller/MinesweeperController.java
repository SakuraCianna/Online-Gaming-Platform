package com.game.controller;

import com.game.entity.Minesweeper;
import com.game.service.MinesweeperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/minesweeper")
public class MinesweeperController {
    private final MinesweeperService minesweeperService;

    public MinesweeperController(MinesweeperService minesweeperService) {
        this.minesweeperService = minesweeperService;
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = minesweeperService.saveData(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getInProgress")
    public Minesweeper getInProgress(@RequestParam("user_id") long userId) {
        return minesweeperService.getInProgressGame(userId);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteById(@RequestParam long id,
            @RequestParam(value = "user_id", required = false) Long userId) {
        Map<String, Object> result = minesweeperService.deleteById(id, userId);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}