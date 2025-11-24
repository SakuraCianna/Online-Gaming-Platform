package com.game.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "ollama")
@Data
public class OllamaProperties {
    private String baseUrl = "http://localhost:11434";
    private String defaultModel = "qwen2.5:7b-instruct";
    private double defaultTemperature = 0.7;
}