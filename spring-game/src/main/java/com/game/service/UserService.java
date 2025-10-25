package com.game.service;

import com.game.config.JwtUtils;
import com.game.config.PasswordUtils;
import com.game.entity.User;
import com.game.exception.BusinessException;
import com.game.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Transactional
public class UserService {
    private final UserMapper userMapper;
    private final GameRecordService gameRecordService;

    public UserService(UserMapper userMapper, GameRecordService gameRecordService) {
        this.userMapper = userMapper;
        this.gameRecordService = gameRecordService;
    }

    public Map<String, Object> RegisterService(Map<String, Object> request) {
        String email = (String) request.get("email");
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String confirmPassword = (String) request.get("confirmPassword");
        String code = (String) request.get("code");

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new BusinessException(400, "用户名不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException(400, "密码不能为空");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException(400, "验证码不能为空");
        }
        if (isValidQQEmail(email)) {
            throw new BusinessException(400, "请输入有效的QQ邮箱");
        }

        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        if (Boolean.TRUE.equals(userMapper.findUsername(username, user.getEmail()))) {
            throw new BusinessException(400, "该用户名已存在");
        }
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            throw new BusinessException(400, "该用户已注册");
        }
        if (LocalDateTime.now().isAfter(user.getVerificationExpireTime())) {
            throw new BusinessException(400, "验证码已过期");
        }
        if (!code.equals(user.getVerificationCode())) {
            throw new BusinessException(400, "验证码错误");
        }

        // 注册逻辑
        String encodedPassword = PasswordUtils.encrypt(password);
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("username", username);
        updateMap.put("password", encodedPassword);
        updateMap.put("avatar", "/image/default-avatar.jpg");
        updateMap.put("email_verified", 1);
        updateMap.put("id", user.getId());

        if (userMapper.registerUpdate(updateMap) > 0) {
            // 添加用户ID到布隆过滤器
            gameRecordService.addUserToBloomFilter(user.getId());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "注册成功");
            response.put("userId", user.getId());
            return response;
        } else {
            throw new BusinessException(500, "注册失败,请联系管理员");
        }
    }

    public Map<String, Object> LoginService(Map<String, Object> request) {
        String email = (String) request.get("email");
        String password = (String) request.get("password");

        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException(400, "密码不能为空");
        }

        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        if (user.getStatus().equals(0)) {
            throw new BusinessException(403, "账号已被封禁");
        }
        if (user.getEmailVerified() != null && user.getEmailVerified() == 0) {
            throw new BusinessException(400, "请先验证邮箱");
        }
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            throw new BusinessException(400, "账户未完成注册");
        }

        if (!PasswordUtils.matches(password, user.getPassword())) {
            throw new BusinessException(400, "密码错误");
        }

        userMapper.updateLogin(LocalDateTime.now(), user.getId());

        user.setPassword(null);
        user.setEmailVerified(null);
        user.setVerificationExpireTime(null);
        user.setVerificationCode(null);

        // 生成JWT token
        String token = JwtUtils.generateToken(String.valueOf(user.getId()));

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "登录成功");
        response.put("user", user); // 给前端返回用户数据
        response.put("token", token); // 返回token
        return response;
    }

    public Map<String, Object> ResetService(Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        String email = (String) request.get("email");
        String password = (String) request.get("newPassword");
        String confirmPassword = (String) request.get("confirmNewPassword");
        String code = (String) request.get("code");

        // 参数校验
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(400, "邮箱不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new BusinessException(400, "密码不能为空");
        }
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(400, "两次输入的密码不一致");
        }
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException(400, "验证码不能为空");
        }
        if (isValidQQEmail(email)) {
            throw new BusinessException(400, "请输入有效的QQ邮箱");
        }

        User user = userMapper.selectByEmail(email);
        if (user == null) {
            throw new BusinessException(400, "用户不存在");
        }
        if (LocalDateTime.now().isAfter(user.getVerificationExpireTime())) {
            throw new BusinessException(400, "验证码已过期");
        }
        if (!code.equals(user.getVerificationCode())) {
            throw new BusinessException(400, "验证码错误");
        }

        if (userMapper.updatePassword(email, PasswordUtils.encrypt(password)) > 0) {
            response.put("success", true);
            response.put("message", "更新密码成功");
            response.put("user", user);
            return response;
        } else {
            throw new BusinessException(500, "系统故障,更新密码失败");
        }
    }

    private boolean isValidQQEmail(String email) {
        // QQ邮箱正则表达式
        String qqEmailRegex = "^[1-9]\\d{4,10}@qq\\.com$";
        // 也支持vip.qq.com等变体
        String qqEmailVipRegex = "^[1-9]\\d{4,10}@(vip|foxmail)\\.qq\\.com$";

        // 检查是否符合QQ邮箱格式
        boolean isBasicQQ = email.matches(qqEmailRegex);
        boolean isVipQQ = email.matches(qqEmailVipRegex);

        return !isBasicQQ && !isVipQQ;
    }

}