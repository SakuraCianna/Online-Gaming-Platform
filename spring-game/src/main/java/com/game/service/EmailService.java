package com.game.service;

import com.game.component.RedisKeyManager;
import com.game.exception.BusinessException;
import com.game.mapper.UserMapper;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
@Transactional
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisKeyManager redisKeyManager;

    public Map<String, Object> sendCode(String to) {
        try {
            // 验证该邮箱是否存在于redis,防止恶意请求
            String key = redisKeyManager.buildEmailLimitKey(to);
            if (stringRedisTemplate.hasKey(key)) {
                throw new BusinessException(400, "请勿频繁发送验证码");
            }
            // 把该邮箱加入redis,防止恶意请求
            stringRedisTemplate.opsForValue().set(key, "1", 2, java.util.concurrent.TimeUnit.MINUTES);

            // 参数校验
            validateEmail(to);
            String code = generateCode();
            System.out.println("验证码:" + code);

            // 1.验证码失效重新获取验证码 2.重置密码
            if(userMapper.selectByEmail(to) != null) {
                sendMail(to, code);
                Map<String, Object> response = new HashMap<>();
                if(userMapper.updateCode(code, LocalDateTime.now().plusMinutes(5), to) > 0) {
                    response.put("success", true);
                    response.put("message", "验证码发送成功");
                    return response;
                } else {
                    throw new BusinessException(500, "验证码存储失败");
                }
            }

            Map<String, Object> insert = new HashMap<>();
            insert.put("username", to);
            insert.put("email", to);
            insert.put("verification_code", code);

            LocalDateTime expireTime = LocalDateTime.now().plusMinutes(5);
            insert.put("verification_expire_time", expireTime);
            insert.put("email_verified", 0);

            int result = userMapper.insertUserCode(insert);
            if (result <= 0) {
                throw new BusinessException(500, "验证码存储失败");
            }

            sendMail(to, code);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "验证码发送成功");
            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "邮件发送失败: " + e.getMessage());
        }
    }

    // 邮箱验证方法
    private void validateEmail(String email) {
        // 空值检查
        if (email == null || email.trim().isEmpty()) {
            throw new BusinessException(400, "邮箱不能为空");
        }

        // QQ邮箱格式验证
        if (!isValidQQEmail(email)) {
            throw new BusinessException(400, "请输入有效的QQ邮箱（格式：数字@qq.com）");
        }
    }

    // QQ邮箱合法性验证
    private boolean isValidQQEmail(String email) {
        // QQ邮箱正则表达式
        String qqEmailRegex = "^[1-9]\\d{4,10}@qq\\.com$";
        // 也支持vip.qq.com等变体
        String qqEmailVipRegex = "^[1-9]\\d{4,10}@(vip|foxmail)\\.qq\\.com$";

        // 检查是否符合QQ邮箱格式
        boolean isBasicQQ = email.matches(qqEmailRegex);
        boolean isVipQQ = email.matches(qqEmailVipRegex);

        return isBasicQQ || isVipQQ;
    }

    private String generateCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    private void sendMail(String to, String code) throws MessagingException {
        Context context = new Context();
        context.setVariable("verificationCode", code);
        context.setVariable("username", to.substring(0, to.indexOf("@")));

        String htmlContent = templateEngine.process("verification-code", context);

        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setFrom("754515922@qq.com");
        helper.setTo(to);
        helper.setSubject("游戏平台 - 邮箱验证码");
        helper.setText(htmlContent, true);

        mailSender.send(mimeMessage);
    }
}