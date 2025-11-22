package com.game.controller;

import com.game.exception.BusinessException;
import com.game.service.GomokuService;
import com.game.vo.PointVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import javax.crypto.interfaces.PBEKey;
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
    public ResponseEntity<Map<String, Object>> startGame(@PathVariable String roomCode, @RequestBody Map<String, Object> request) {
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
        Map<String, Object> result = new HashMap<>();
        try {
            result = gomokuService.AI_Move(request);
            result.put("code", 200);
            result.put("data", result.remove("move"));
            return ResponseEntity.ok(result);
        } catch (BusinessException e) {
            result.put("code", e.getCode());  // 4001 表示AI无效落子
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.OK).body(result);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("success", false);
            result.put("message", "AI计算失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }

    @PostMapping("/{roomCode}/end")
    public ResponseEntity<Map<String, Object>> endGame(@PathVariable String roomCode,@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gomokuService.endGame(roomCode,request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

}
