package com.game.controller;

import com.game.entity.Game2048;
import com.game.mapper.Game2048Mapper;
import com.game.service.Game2048Service;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/2048")
public class Game2048Controller {
    private final Game2048Service game2048Service;
    private final Game2048Mapper game2048Mapper;
    public Game2048Controller(Game2048Service game2048Service, Game2048Mapper game2048Mapper) {
        this.game2048Service = game2048Service;
        this.game2048Mapper = game2048Mapper;
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = game2048Service.saveData(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @Transactional(readOnly = true)
    @GetMapping("/getInProgress")
    public Game2048 getInProgress(@RequestParam long user_id) {
        return game2048Mapper.selectInProgressByUserId(user_id);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteById(@RequestParam long id) {
        Map<String, Object> result = game2048Service.deleteById(id);
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
