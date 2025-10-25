package com.game.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {
    private static String SECRET_KEY;

    @Value("${jwt.secret}")
    public void setSecretKey(String secretKey) {
        JwtUtils.SECRET_KEY = secretKey;
    }

    // 生成 Token
    public static String generateToken(String userId) {
        long now = System.currentTimeMillis();
        long expire = 1000 * 60 * 60 * 24; // 24小时
        Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject(userId)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + expire))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 验证 Token 并返回 Claims
    public static Claims verifyToken(String token) {
        Key key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // 解析 userId
    public static Long getUserIdFromToken(String token) {
        Claims claims = verifyToken(token);
        return Long.valueOf(claims.getSubject());
    }
}