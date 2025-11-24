package com.game.config;

import org.springframework.stereotype.Component;

@Component
public class AiModelConfig {
    // 智谱AI模型
    public static final String MODEL_GLM_4_5 = "glm-4.5";
    public static final String MODEL_GLM_4_5_AIR = "glm-4.5-air";
    public static final String MODEL_GLM_4_5_X = "glm-4.5-x";
    public static final String MODEL_GLM_4_6 = "glm-4.6";
    public static final String MODEL_GLM_4_5_AIRX = "GLM-4.5-AirX";
    
    // Ollama模型
    public static final String MODEL_QWEN_7B_INSTRUCT = "qwen2.5:7b-instruct";
    public static final String MODEL_GEMMA_2B = "gemma2:2b";
    public static final String MODEL_QWEN_1_5B = "qwen2.5:1.5b";
}