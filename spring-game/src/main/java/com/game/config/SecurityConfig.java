package com.game.config;

import com.game.component.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 匿名访问：登录、注册、重置密码、邮件验证码、健康检查
                        .requestMatchers("/api/user/login", "/api/user/register", "/api/user/resetPassword").permitAll()
                        .requestMatchers("/api/email/**").permitAll()
                        .requestMatchers("/api/user/health").permitAll()
                        // WebSocket 端点（由 JwtHandshakeInterceptor 单独校验）
                        .requestMatchers("/ws/**").permitAll()
                        // Swagger 文档
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // 其余所有 /api/** 都需要认证
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                // 自定义未认证请求的异常处理，返回 401 而不是 403
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            log.warn("未认证访问: {} - {}", request.getRequestURI(), authException.getMessage());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期，请重新登录\"}");
                        }))
                // 添加 JWT 认证过滤器
                .addFilterBefore(new JwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                // 添加安全响应头
                .headers(headers -> headers
                        .contentTypeOptions(withDefaults())
                        .frameOptions(withDefaults()));

        return http.build();
    }

    /**
     * JWT 认证过滤器（内部类）
     * 从 Authorization 头中提取 JWT token，验证后将用户 ID 写入 SecurityContext
     */
    private static class JwtAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(@NonNull HttpServletRequest request,
                                        @NonNull HttpServletResponse response,
                                        @NonNull FilterChain filterChain) throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");

            // 如果请求带有 Bearer token
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                try {
                    // 验证 token 并提取 userId
                    Claims claims = JwtUtil.verifyToken(token);
                    Long userId = Long.valueOf(claims.getSubject());

                    // 将用户信息存入 SecurityContext
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                } catch (Exception e) {
                    // token 无效或过期，返回 401
                    log.warn("JWT 验证失败: {}", e.getMessage());
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"token无效或已过期\"}");
                    return;
                }
            }

            filterChain.doFilter(request, response);
        }
    }
}
