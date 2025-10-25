package com.game.controller;

import com.game.config.OllamaProperties;
import com.game.service.OllamaService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/ollama")
public class OllamaController {

    private final OllamaProperties ollamaProperties;
    private final OllamaService ollamaService;
    private final RestTemplate restTemplate = new RestTemplate();

    public OllamaController(OllamaProperties ollamaProperties, OllamaService ollamaService) {
        this.ollamaProperties = ollamaProperties;
        this.ollamaService = ollamaService;
    }

    @GetMapping("/getModels")
    public ResponseEntity<String> getModels() {
        try {
            String url = ollamaProperties.getBaseUrl() + "/v1/models";
            String response = restTemplate.getForObject(url, String.class);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/chat")
    public ResponseEntity<String> chatWithOllama(@RequestParam String prompt) {
        try {
            String url = ollamaProperties.getBaseUrl() + "/v1/chat/completions";

            String encodedPrompt = URLEncoder.encode(prompt, StandardCharsets.UTF_8);

            String requestBody = String.format("{\"model\":\"%s\",\"messages\":[{\"role\":\"user\",\"content\":\"%s\"}]}",
                    ollamaProperties.getChat().getOptions().getModel(),
                    encodedPrompt);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(url, entity, String.class);
            System.out.println(response);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

}