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
import org.springframework.lang.Nullable;
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
        
        // 优先从 Authorization header 获取 token（更安全）
        String token = null;
        String authHeader = servletRequest.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        
        // 兼容：如果 header 中没有，尝试从 URL 参数获取（向后兼容）
        if (token == null || token.trim().isEmpty()) {
            token = servletRequest.getParameter("token");
        }

        // 检查token是否存在
        if (token == null || token.trim().isEmpty()) {
            log.warn("WebSocket握手失败: 缺少token");
            return false;
        }

        // 尝试解析token
        try {
            Long userId = JwtUtil.getUserIdFromToken(token);

            // 检查userId是否有效
            if (userId == null || userId <= 0) {
                log.warn("WebSocket握手失败: 无效的userId");
                return false;
            }

            // 握手成功
            attributes.put("userId", userId);
            return true;

        } catch (ExpiredJwtException e) {
            log.warn("WebSocket握手失败: JWT token已过期");
            return false;
        } catch (MalformedJwtException e) {
            log.warn("WebSocket握手失败: JWT token格式错误");
            return false;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.warn("WebSocket握手失败: JWT签名验证失败");
            return false;
        } catch (Exception e) {
            log.error("WebSocket握手失败: token验证异常", e);
            return false;
        }
    }

    @Override
    public void afterHandshake(@NonNull ServerHttpRequest request,
            @NonNull ServerHttpResponse response,
            @NonNull WebSocketHandler wsHandler,
            @Nullable Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手后发生异常", exception);
        }
    }
}
