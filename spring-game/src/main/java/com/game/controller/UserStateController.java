package com.game.controller;

import com.game.component.JwtUtil;
import com.game.service.UserStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/state")
@RequiredArgsConstructor
public class UserStateController {

    private final UserStateService userStateService;

    @PostMapping("/online")
    public Map<String, Object> online() {
        Long userId = JwtUtil.getCurrentUserId();
        userStateService.markOnline(userId);
        return Map.of("success", true);
    }

    @PostMapping("/offline")
    public Map<String, Object> offline() {
        Long userId = JwtUtil.getCurrentUserId();
        userStateService.markOffline(userId);
        return Map.of("success", true);
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat() {
        Long userId = JwtUtil.getCurrentUserId();
        userStateService.heartbeat(userId);
        return Map.of("success", true);
    }

    @PostMapping("/game/start")
    public Map<String, Object> start(@RequestParam String game) {
        Long userId = JwtUtil.getCurrentUserId();
        userStateService.setCurrentGame(userId, game);
        return Map.of("success", true);
    }

    @PostMapping("/game/stop")
    public Map<String, Object> stop() {
        Long userId = JwtUtil.getCurrentUserId();
        userStateService.clearCurrentGame(userId);
        return Map.of("success", true);
    }

    @GetMapping
    public Map<String, Object> get(@RequestParam long userId) {
        // 查询其他用户状态时仍需要传 userId，保留此接口供查询好友状态使用
        return userStateService.getState(userId);
    }
}