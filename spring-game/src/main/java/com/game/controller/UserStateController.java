package com.game.controller;

import com.game.service.UserStateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user/state")
public class UserStateController {

    private final UserStateService userStateService;

    public UserStateController(UserStateService userStateService) {
        this.userStateService = userStateService;
    }

    @PostMapping("/online")
    public Map<String, Object> online(@RequestParam long userId) {
        userStateService.markOnline(userId);
        return Map.of("success", true);
    }

    @PostMapping("/offline")
    public Map<String, Object> offline(@RequestParam long userId) {
        userStateService.markOffline(userId);
        return Map.of("success", true);
    }

    @PostMapping("/heartbeat")
    public Map<String, Object> heartbeat(@RequestParam long userId) {
        userStateService.heartbeat(userId);
        return Map.of("success", true);
    }

    @PostMapping("/game/start")
    public Map<String, Object> start(@RequestParam long userId, @RequestParam String game) {
        userStateService.setCurrentGame(userId, game);
        return Map.of("success", true);
    }

    @PostMapping("/game/stop")
    public Map<String, Object> stop(@RequestParam long userId) {
        userStateService.clearCurrentGame(userId);
        return Map.of("success", true);
    }

    @GetMapping
    public Map<String, Object> get(@RequestParam long userId) {
        return userStateService.getState(userId);
    }
}