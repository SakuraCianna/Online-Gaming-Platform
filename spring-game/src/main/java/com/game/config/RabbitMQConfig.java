package com.game.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitMQConfig {
    // 队列和交换机常量
    // 邮件相关
    public static final String EMAIL_QUEUE = "game.email.queue";
    public static final String EMAIL_EXCHANGE = "game.email.exchange";
    public static final String EMAIL_ROUTING_KEY = "email.send";

    // 游戏记录相关
    public static final String GAME_RECORD_QUEUE = "game.record.queue";
    public static final String GAME_RECORD_EXCHANGE = "game.record.exchange";
    public static final String GAME_RECORD_ROUTING_KEY = "game.record.save";

    // 好友通知相关
    public static final String FRIEND_NOTIFY_QUEUE = "game.friend.notify.queue";
    public static final String FRIEND_NOTIFY_EXCHANGE = "game.friend.notify.exchange";
    public static final String FRIEND_NOTIFY_ROUTING_KEY = "friend.notify";

    /**
     * 配置消息转换器（使用JSON格式）
     * 用于自动序列化/反序列化Java对象
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 自定义 RabbitTemplate 配置
     * 添加消息发送确认和返回回调
     */
    @Bean
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> {
            // 消息发送确认回调
            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
                if (ack) {
                    System.out.println("✅ RabbitMQ消息发送成功");
                } else {
                    System.err.println("❌ RabbitMQ消息发送失败: " + cause);
                }
            });

            // 消息返回回调（无法路由时触发）
            rabbitTemplate.setReturnsCallback(returned -> {
                System.err.println("⚠️ RabbitMQ消息无法路由:");
                System.err.println("  消息内容: " + returned.getMessage());
                System.err.println("  回复码: " + returned.getReplyCode());
                System.err.println("  回复文本: " + returned.getReplyText());
                System.err.println("  交换机: " + returned.getExchange());
                System.err.println("  路由键: " + returned.getRoutingKey());
            });
        };
    }

    /**
     * 游戏记录队列（用于异步保存游戏结果）
     */
    @Bean
    public Queue gameRecordQueue() {
        return QueueBuilder.durable(GAME_RECORD_QUEUE)
                .maxLength(50000) // 游戏记录量可能较大
                .build();
    }

    /**
     * 游戏记录交换机
     */
    @Bean
    public DirectExchange gameRecordExchange() {
        return new DirectExchange(GAME_RECORD_EXCHANGE);
    }

    /**
     * 游戏记录队列绑定
     */
    @Bean
    public Binding gameRecordBinding() {
        return BindingBuilder.bind(gameRecordQueue())
                .to(gameRecordExchange())
                .with(GAME_RECORD_ROUTING_KEY);
    }

    /**
     * 好友通知队列（用于好友请求、消息通知）
     */
    @Bean
    public Queue friendNotifyQueue() {
        return QueueBuilder.durable(FRIEND_NOTIFY_QUEUE)
                .ttl(600000) // 消息TTL 10分钟
                .maxLength(20000)
                .build();
    }

    /**
     * 好友通知交换机（主题交换机，支持通配符路由）
     */
    @Bean
    public TopicExchange friendNotifyExchange() {
        return new TopicExchange(FRIEND_NOTIFY_EXCHANGE);
    }

    /**
     * 好友通知队列绑定（使用通配符 friend.* 匹配所有好友相关消息）
     */
    @Bean
    public Binding friendNotifyBinding() {
        return BindingBuilder.bind(friendNotifyQueue())
                .to(friendNotifyExchange())
                .with("friend.*");
    }
}