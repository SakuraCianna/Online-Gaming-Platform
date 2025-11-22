package com.game.controller;

import com.game.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.Random;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;

    @PostMapping("/sendCode")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, Object> request) {
        String email = (String) request.get("email");
        Map<String, Object> result = emailService.sendCode(email);
        return ResponseEntity.ok(result);
    }
}