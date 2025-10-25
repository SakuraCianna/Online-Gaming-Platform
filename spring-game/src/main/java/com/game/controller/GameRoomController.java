package com.game.controller;

import com.game.service.GameRoomService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/room")
public class GameRoomController {
    private final GameRoomService gameRoomService;

    public GameRoomController(GameRoomService gameRoomService) {
        this.gameRoomService = gameRoomService;
    }

    @GetMapping("/checkRoomCode")
    public ResponseEntity<Map<String, Object>> checkRoomCode(@RequestParam String roomCode) {
        Map<String, Object> result = gameRoomService.checkRoomCode(roomCode);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createRoom(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.createRoom(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> leaveRoom(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.leaveRoom(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/invite")
    public ResponseEntity<Map<String, Object>> inviteToRoom(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.inviteToRoom(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/acceptInvite")
    public ResponseEntity<Map<String, Object>> acceptInvite(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.acceptInvite(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/rejectInvite")
    public ResponseEntity<Map<String, Object>> rejectInvite(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.rejectInvite(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/players")
    public ResponseEntity<Map<String, Object>> getRoomPlayers(@RequestParam String roomCode) {
        Map<String, Object> result = gameRoomService.getRoomPlayers(roomCode);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/kick")
    public ResponseEntity<Map<String, Object>> kickPlayer(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.kickPlayer(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/addAI")
    public ResponseEntity<Map<String, Object>> addAI(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = gameRoomService.addAI(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

}
