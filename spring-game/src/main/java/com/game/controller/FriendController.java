package com.game.controller;

import com.game.entity.Friend;
import com.game.mapper.FriendMapper;
import com.game.service.FriendService;
import com.game.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/friend")
@RequiredArgsConstructor
public class FriendController {
    private final FriendService friendService;
    private final FriendMapper friendMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/getAllFriend")
    public ResponseEntity<Map<String, Object>> searchAllFriend(@RequestParam long id,
                                                               @RequestParam int page,
                                                               @RequestParam int pageSize) {
        Map<String, Object> result = friendService.getAllFriend(id,page,pageSize);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @Transactional
    @DeleteMapping("/delete")
    public void delete(@RequestParam long userId, @RequestParam long friendId) {
        friendMapper.deleteFriend(userId, friendId);
        messagingTemplate.convertAndSend("/topic/friendDelete/" + friendId, "deleted");
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchFriend(@RequestParam long id,
                                                            @RequestParam String keyword,
                                                            @RequestParam int page,
                                                            @RequestParam int pageSize) {
        Map<String,Object> result = friendService.searchFriend(id,keyword,page,pageSize);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addFriend(@RequestBody Map<String, Object> params) {
        long userId = Long.parseLong(params.get("userId").toString());
        long friendId = Long.parseLong(params.get("friendId").toString());
        String message = params.get("message").toString();

        Map<String, Object> result = friendService.addFriend(userId, friendId, message);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/confirm")
    public ResponseEntity<Map<String, Object>> confirmFriend(@RequestBody Map<String, Object> params) {
        long userId = Long.parseLong(params.get("userId").toString());
        long friendId = Long.parseLong(params.get("friendId").toString());
        int status = Integer.parseInt(params.get("action").toString());

        Map<String, Object> result = friendService.confirmFriend(userId, friendId, status);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }
}
