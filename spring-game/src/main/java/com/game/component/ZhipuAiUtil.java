package com.game.component;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ZhipuAiUtil {

    private final ZhipuAiClient zhipuAiClient;

    /**
     * 简单聊天 - 单轮对话
     */
    public String simpleChat(String prompt) {
        return simpleChat(prompt, "glm-4.6");
    }

    /**
     * 简单聊天 - 指定模型
     */
    public String simpleChat(String prompt, String model) {
        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(List.of(
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER.value())
                                .content(prompt)
                                .build()
                ))
                .maxTokens(2048)
                .temperature(0.7f)
                .build();

        return executeChat(request);
    }

    /**
     * 多轮对话
     */
    public String multiRoundChat(List<ChatMessage> messages) {
        return multiRoundChat(messages, "glm-4.6");
    }

    /**
     * 多轮对话 - 指定模型
     */
    public String multiRoundChat(List<ChatMessage> messages, String model) {
        ChatCompletionCreateParams request = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages)
                .maxTokens(2048)
                .temperature(0.7f)
                .build();

        return executeChat(request);
    }

    /**
     * 系统角色聊天 - 带系统提示词
     */
    public String chatWithSystemRole(String systemPrompt, String userPrompt) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM.value())
                .content(systemPrompt)
                .build());
        messages.add(ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(userPrompt)
                .build());

        return multiRoundChat(messages);
    }

    /**
     * 高级配置聊天
     * 修复版本 - 使用var避免泛型问题
     */
    public String advancedChat(List<ChatMessage> messages, String model,
                               Integer maxTokens, Float temperature,
                               Boolean enableThinking) {
        // 使用 var 避免泛型通配符问题
        var builder = ChatCompletionCreateParams.builder()
                .model(model)
                .messages(messages);

        if (maxTokens != null) {
            builder = builder.maxTokens(maxTokens);
        }
        if (temperature != null) {
            builder = builder.temperature(temperature);
        }
        if (enableThinking != null && enableThinking) {
            builder = builder.thinking(ChatThinking.builder().type("enabled").build());
        }

        return executeChat(builder.build());
    }

    /**
     * 执行聊天请求
     */
    private String executeChat(ChatCompletionCreateParams request) {
        try {
            ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(request);

            if (response.isSuccess()) {
                ChatMessage message = response.getData().getChoices().getFirst().getMessage();
                return message.getContent().toString();
            } else {
                log.error("智谱AI调用失败: {}", response.getMsg());
                throw new RuntimeException("AI调用失败: " + response.getMsg());
            }
        } catch (Exception e) {
            log.error("智谱AI调用异常", e);
            throw new RuntimeException("AI调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * 构建用户消息
     */
    public ChatMessage buildUserMessage(String content) {
        return ChatMessage.builder()
                .role(ChatMessageRole.USER.value())
                .content(content)
                .build();
    }

    /**
     * 构建助手消息
     */
    public ChatMessage buildAssistantMessage(String content) {
        return ChatMessage.builder()
                .role(ChatMessageRole.ASSISTANT.value())
                .content(content)
                .build();
    }

    /**
     * 构建系统消息
     */
    public ChatMessage buildSystemMessage(String content) {
        return ChatMessage.builder()
                .role(ChatMessageRole.SYSTEM.value())
                .content(content)
                .build();
    }
}