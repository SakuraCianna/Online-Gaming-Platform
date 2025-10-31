package com.game.controller;

import com.game.entity.Game2048;
import com.game.service.Game2048Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/2048")
public class Game2048Controller {
    private final Game2048Service game2048Service;

    public Game2048Controller(Game2048Service game2048Service) {
        this.game2048Service = game2048Service;
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = game2048Service.saveData(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getInProgress")
    public Game2048 getInProgress(@RequestParam("user_id") long userId) {
        return game2048Service.getInProgressGame(userId);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteById(@RequestParam long id,
            @RequestParam(value = "user_id", required = false) Long userId) {
        Map<String, Object> result = game2048Service.deleteById(id, userId);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateData(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = game2048Service.updateData(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}
