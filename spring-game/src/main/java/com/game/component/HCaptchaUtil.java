package com.game.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.Map;

@Component
public class HCaptchaUtil {

    private static final String HCAPTCHA_VERIFY_URL = "https://hcaptcha.com/siteverify";

    @Value("${hcaptcha.secret}")
    private String secretKey;

    /**
     * 验证 hCaptcha token
     * @param token 前端传来的 hCaptcha token
     * @return 验证是否成功
     */
    @SuppressWarnings({"rawtypes"})
    public boolean verify(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }

        try {
            RestTemplate restTemplate = new RestTemplate();

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("secret", secretKey);
            params.add("response", token);

            ResponseEntity<Map> response = restTemplate.postForEntity(
                    HCAPTCHA_VERIFY_URL,
                    params,
                    Map.class
            );

            Map body = response.getBody();
            if (body != null && body.containsKey("success")) {
                return (Boolean) body.get("success");
            }

            return false;
        } catch (Exception e) {
            System.err.println("hCaptcha 验证失败: " + e.getMessage());
            return false;
        }
    }
}