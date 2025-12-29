package com.game.config;

import com.game.Interceptor.JwtHandshakeInterceptor;
import com.game.Interceptor.WebSocketRateLimitInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * WebSocket配置类
 * 实现高级特性：心跳检测、连接超时、消息限流
 */
@EnableWebSocketMessageBroker
@Configuration
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketRateLimitInterceptor rateLimitInterceptor;

    public WebSocketConfig(WebSocketRateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*")
                .addInterceptors(new JwtHandshakeInterceptor())
                .withSockJS()
                // SockJS心跳间隔（25秒，略小于服务端心跳）
                .setHeartbeatTime(25000)
                // 断开检测超时（5秒）
                .setDisconnectDelay(5000)
                // 客户端发送消息超时（10秒）
                .setHttpMessageCacheSize(1000);
    }

    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // 配置SimpleBroker，启用心跳检测
        // 参数：服务端心跳间隔10秒，客户端心跳间隔10秒
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                .setHeartbeatValue(new long[]{10000, 10000})  // [服务端发送间隔, 期望客户端发送间隔]
                .setTaskScheduler(heartbeatScheduler());
        
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureWebSocketTransport(@NonNull WebSocketTransportRegistration registration) {
        // 消息大小限制（64KB）
        registration.setMessageSizeLimit(64 * 1024);
        // 发送缓冲区大小限制（512KB）
        registration.setSendBufferSizeLimit(512 * 1024);
        // 发送超时（30秒）
        registration.setSendTimeLimit(30 * 1000);
    }

    @Override
    public void configureClientInboundChannel(@NonNull ChannelRegistration registration) {
        // 添加限流拦截器
        registration.interceptors(rateLimitInterceptor);
    }

    /**
     * 心跳调度器
     */
    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }
}