package com.game.Interceptor;

import com.game.component.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @NonNull Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest)) {
            return false;
        }

        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String token = servletRequest.getParameter("token");

        // 检查token是否存在
        if (token == null || token.trim().isEmpty()) {
            return false;
        }

        // 尝试解析token
        try {
            Long userId = JwtUtil.getUserIdFromToken(token);

            // 检查userId是否有效
            if (userId <= 0) {
                return false;
            }

            // 握手成功
            attributes.put("userId", userId);
            return true;

        } catch (ExpiredJwtException e) {
            log.error("❌ WebSocket握手失败: JWT token已过期", e);
            return false;
        } catch (MalformedJwtException e) {
            log.error("❌ WebSocket握手失败: JWT token格式错误", e);
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) { // <--- 修改后的 catch 块
            log.error("❌ WebSocket握手失败: JWT签名验证失败", e);
            return false;
        } catch (Exception e) {
            log.error("❌ WebSocket握手失败: token验证异常", e);
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            log.error("❌ WebSocket握手后发生异常", exception);
        }
    }
}
