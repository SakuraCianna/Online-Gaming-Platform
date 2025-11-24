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

    @Deprecated
    @PostMapping("/ai/move")
    public ResponseEntity<Map<String, Object>> getAIMove(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 410);  // 410 Gone - 资源已不存在
        result.put("success", false);
        result.put("message", "此接口已废弃，AI逻辑已迁移到前端实现");
        return ResponseEntity.status(HttpStatus.GONE).body(result);
    }

    @PostMapping("/{roomCode}/end")
    public ResponseEntity<Map<String, Object>> endGame(@PathVariable String roomCode,@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gomokuService.endGame(roomCode,request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

}
