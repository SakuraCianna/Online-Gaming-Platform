package com.game.controller;

import com.game.service.GomokuService;
import com.game.vo.PointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/gomoku")
@RequiredArgsConstructor
public class GomokuController {
    private final GomokuService gomokuService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/{roomCode}/swapColor")
    public ResponseEntity<Map<String, Object>> swapColor(
            @PathVariable String roomCode,
            @RequestParam Boolean isBlackFirst,
            @RequestParam Long userId) {
        // 广播给房间内所有成员
        messagingTemplate.convertAndSend("/topic/room/" + roomCode, Map.of(
                "type", "swapColor",
                "isBlackFirst", isBlackFirst,
                "userId", userId));
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "颜色交换成功"));
    }

    @PostMapping("/{roomCode}/start")
    public ResponseEntity<Map<String, Object>> startGame(@PathVariable String roomCode,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = gomokuService.startGame(roomCode, request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{roomCode}/move")
    public ResponseEntity<Map<String, Object>> makeMove(@PathVariable String roomCode, @RequestBody PointVO point) {
        Map<String, Object> result = gomokuService.makeMove(roomCode, point);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/ai/move")
    public ResponseEntity<Map<String, Object>> getAIMove(@RequestBody Map<String, Object> request) {
        try {
            // 参数验证
            if (request.get("board") == null) {
                throw new IllegalArgumentException("棋盘数据不能为空");
            }
            if (request.get("aiColor") == null || request.get("playerColor") == null) {
                throw new IllegalArgumentException("颜色参数不能为空");
            }

            // 处理board的类型转换
            int[][] board;
            Object boardObj = request.get("board");
            if (boardObj instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.List<Integer>> boardList = (java.util.List<java.util.List<Integer>>) boardObj;
                board = new int[boardList.size()][];
                for (int i = 0; i < boardList.size(); i++) {
                    java.util.List<Integer> row = boardList.get(i);
                    board[i] = new int[row.size()];
                    for (int j = 0; j < row.size(); j++) {
                        board[i][j] = row.get(j);
                    }
                }
            } else {
                board = (int[][]) boardObj;
            }

            int aiColor = ((Number) request.get("aiColor")).intValue();
            int playerColor = ((Number) request.get("playerColor")).intValue();
            String difficulty = (String) request.getOrDefault("difficulty", "hard");

            // 调用AI算法（根据难度分配）
            Map<String, Integer> move = gomokuService.makeMoveByAI(board, aiColor, playerColor, difficulty);

            Map<String, Object> result = new HashMap<>();
            result.put("code", 200);
            result.put("success", true);
            result.put("message", "AI计算成功");
            result.put("move", move);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 500);
            result.put("success", false);
            result.put("message", "AI计算失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/{roomCode}/end")
    public ResponseEntity<Map<String, Object>> endGame(@PathVariable String roomCode,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> result = gomokuService.endGame(roomCode, request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

}
