package com.game.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.config.AiModelConfig;
import com.game.config.OllamaProperties;
import com.game.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
@Component
public class OllamaUtil implements ApplicationRunner {
    private final OllamaProperties ollamaProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String simpleChat(String userPrompt, String model) {
        return callOllama(userPrompt, model, ollamaProperties.getDefaultTemperature());
    }

    public String advancedChat(String systemPrompt, String userPrompt, String model, Double temperature) {
        String prompt = systemPrompt + "\n\n" +  userPrompt;
        return callOllama(prompt, model, temperature != null ? temperature : ollamaProperties.getDefaultTemperature());
    }

    private String callOllama(String prompt, String model, double temperature) {
        try {
            String url = ollamaProperties.getBaseUrl() + "/api/generate";
            Map<String, Object> request = new HashMap<>();
            request.put("model", model);
            request.put("prompt", prompt);
            request.put("stream", false);

            Map<String, Object> options = new HashMap<>();
            options.put("temperature", temperature);
            request.put("options", options);

            String response =  restTemplate.postForObject(url, request, String.class);

            @SuppressWarnings("unchecked")
            Map<String, Object> resMap = objectMapper.readValue(response,Map.class);

            return (String) resMap.get("response");
        } catch (Exception e) {
            log.error("Ollama调用异常: model={}, error={}", model, e.getMessage(), e);
            throw new BusinessException(500, e.getMessage());
        }
    }

    public void warmingUp() {
        try {
            String warmupPrompt = "你好,请简单回复'已就绪'即可,不需要输出别的回答";
            String response = simpleChat(warmupPrompt, AiModelConfig.MODEL_QWEN_7B_INSTRUCT);
        } catch (Exception e) {
            log.error("预热失败{}", String.valueOf(e));
        }
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("模型开始预热");
        warmingUp();
        log.info("预热成功");
    }

}
