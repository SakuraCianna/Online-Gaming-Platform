package com.game.controller;

import com.game.entity.FriendChat;
import com.game.mapper.FriendChatMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final FriendChatMapper friendChatMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @PostMapping("sendFriendMessage")
    @Transactional
    public void sendFriendMessage(@RequestBody Map<String, Object> msg) {
        Long userId = Long.parseLong(msg.get("userId").toString());
        Long friendId = Long.parseLong(msg.get("friendId").toString());
        String content = msg.get("message").toString();

        FriendChat friendChat = new FriendChat();
        friendChat.setSenderId(userId);
        friendChat.setReceiverId(friendId);
        friendChat.setContent(content);
        friendChat.setCreatedAt(LocalDateTime.now());
        friendChat.setIsRead(0);

        friendChatMapper.insert(friendChat);
        // 双向发送
        messagingTemplate.convertAndSend("/topic/chat/" + friendId, friendChat);
        messagingTemplate.convertAndSend("/topic/chat/" + userId, friendChat);
    }

    @GetMapping("/getFriendChatHistory")
    public List<FriendChat> getFriendChatHistory(@RequestParam long userId, @RequestParam long friendId) {
        return friendChatMapper.getFriendChatHistory(userId, friendId);
    }

    @GetMapping("/unReadFriendChat")
    public List<Map<String, Object>> getUnreadFriendChat(@RequestParam long userId) {
        return friendChatMapper.getUnreadFriendChat(userId);
    }

    @PostMapping("/markFriendChatRead")
    @Transactional
    public void markFriendChatRead(@RequestBody Map<String, Object> msg) {
        Long userId = Long.parseLong(msg.get("userId").toString());
        Long friendId = Long.parseLong(msg.get("friendId").toString());
        friendChatMapper.markFriendChatRead(userId, friendId);
    }
}
