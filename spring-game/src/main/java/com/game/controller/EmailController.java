package com.game.controller;

import com.game.component.HCaptchaUtil;
import com.game.exception.BusinessException;
import com.game.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/email")
@RequiredArgsConstructor
public class EmailController {
    private final EmailService emailService;
    private final HCaptchaUtil hCaptchaUtil;

    @PostMapping("/sendCode")
    public ResponseEntity<Map<String, Object>> sendCode(@RequestBody Map<String, Object> request) {
        // 人机验证
        String hcaptchaToken = (String) request.get("hcaptchaToken");
        if (hcaptchaToken == null || hcaptchaToken.isEmpty()) {
            throw new BusinessException(400, "请完成人机验证");
        }
        if (!hCaptchaUtil.verify(hcaptchaToken)) {
            throw new BusinessException(400, "人机验证失败，请重试");
        }
        
        String email = (String) request.get("email");
        Map<String, Object> result = emailService.sendCode(email);
        return ResponseEntity.ok(result);
    }
}