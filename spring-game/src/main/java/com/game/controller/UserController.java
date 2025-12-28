package com.game.controller;

import com.game.component.HCaptchaUtil;
import com.game.component.JwtUtil;
import com.game.mapper.FriendMapper;
import com.game.mapper.UserMapper;
import com.game.service.UserService;
import com.game.vo.UserVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final UserMapper userMapper;
    private final FriendMapper friendMapper;
    private final HCaptchaUtil hCaptchaUtil;

    /**
     * 从 Authorization header 解析用户ID
     */
    private Long getUserIdFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new RuntimeException("缺少有效的认证token");
        }
        String token = authHeader.substring(7);
        Claims claims = JwtUtil.verifyToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam("file") MultipartFile file) {
        Long userId = getUserIdFromToken(authHeader);
        Map<String, Object> result = userService.uploadAvatar(userId, file);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/update")
    public ResponseEntity<Map<String, Object>> updateUser(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {
        Long userId = getUserIdFromToken(authHeader);
        String username = (String) request.get("username");
        Map<String, Object> result = userService.updateUserInfo(userId, username);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    /**
     * 修改密码
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> request) {
        Long userId = getUserIdFromToken(authHeader);
        String oldPassword = (String) request.get("oldPassword");
        String newPassword = (String) request.get("newPassword");
        Map<String, Object> result = userService.changePassword(userId, oldPassword, newPassword);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        String hcaptchaToken = (String) request.get("hcaptchaToken");
        if (hcaptchaToken == null || hcaptchaToken.isEmpty()) {
            result.put("success", false);
            result.put("message", "请完成人机验证");
            result.put("code", 400);
            return ResponseEntity.ok(result);
        }

        // 使用注入的实例调用 verify 方法
        boolean isHumanVerified = hCaptchaUtil.verify(hcaptchaToken);
        if (!isHumanVerified) {
            result.put("success", false);
            result.put("message", "人机验证失败，请重试");
            result.put("code", 400);
            return ResponseEntity.ok(result);
        }

        result = userService.RegisterService(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        Map<String, Object> result = userService.LoginService(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/resetPassword")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> request){
        Map<String,Object> result = userService.ResetService(request);
        result.put("code", 200);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/verifyToken")
    public ResponseEntity<?> verifyToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("缺少有效的认证token");
        }
        String token = authHeader.substring(7);
        try {
            Claims claims = JwtUtil.verifyToken(token);
            return ResponseEntity.ok("Token is valid for user: " + claims.getSubject());
        } catch (Exception e) {
            return ResponseEntity.status(401).body("token无效或已过期");
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchByUsername(@RequestParam String username, @RequestParam long id) {
        Map<String, Object> result = new HashMap<>();
        UserVO userVO = userMapper.searchByUsername(username);
        if (userVO == null) {
            result.put("code", 404);
            result.put("msg", "用户不存在");
            result.put("data", null);
        } else {
            boolean isFriend = friendMapper.isFriend(id, userVO.getId());
            Map<String, Object> data = new HashMap<>();
            data.put("user", userVO);
            data.put("isFriend", isFriend);

            result.put("code", 200);
            result.put("msg", "success");
            result.put("data", data);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(health);
    }
}