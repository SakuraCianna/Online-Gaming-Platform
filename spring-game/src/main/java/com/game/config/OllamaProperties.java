package com.game.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "spring.ai.ollama")
@Data
public class OllamaProperties {
    private String baseUrl;
    private ChatOptions chat;

    @Data
    public static class ChatOptions {
        private Options options;

        @Data
        public static class Options {
            private String model;

            @JsonProperty("num_ctx")
            private int numCtx;

            @JsonProperty("num_thread")
            private int numThread;

            @JsonProperty("num_batch")
            private int numBatch;

            private double temperature;

            @JsonProperty("top_p")
            private double topP;

            @JsonProperty("repeat_penalty")
            private double repeatPenalty;
        }
    }
}