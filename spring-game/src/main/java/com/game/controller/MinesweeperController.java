package com.game.controller;

import com.game.entity.Minesweeper;
import com.game.mapper.MinesweeperMapper;
import com.game.service.MinesweeperService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/minesweeper")
public class MinesweeperController {
    private final MinesweeperService minesweeperService;
    private final MinesweeperMapper minesweeperMapper;

    public MinesweeperController(MinesweeperService minesweeperService,MinesweeperMapper minesweeperMapper) {
        this.minesweeperService = minesweeperService;
        this.minesweeperMapper = minesweeperMapper;
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = minesweeperService.saveData(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/getInProgress")
    public Minesweeper getInProgress(@RequestParam long user_id) {
        return minesweeperMapper.selectInProgressByUserId(user_id);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteById(@RequestParam long id) {
        Map<String, Object> result = minesweeperService.deleteById(id);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/update")
    public ResponseEntity<Map<String, Object>> updateData(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = minesweeperService.updateData(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}
