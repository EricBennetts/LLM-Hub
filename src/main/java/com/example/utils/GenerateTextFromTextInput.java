package com.example.utils;

import com.example.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GenerateTextFromTextInput {
    
    private final AiConfig aiConfig;

    @Autowired
    public GenerateTextFromTextInput(AiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    private String AIGen(String prompt) throws IOException, InterruptedException {
        if (aiConfig == null || aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            throw new RuntimeException("AI配置未初始化或API密钥未设置");
        }

        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt 不能为空");
        }

        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> requestBody = Map.of(
                "model", aiConfig.getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", "You are a helpful assistant."),
                        Map.of("role", "user", "content", prompt)
                ),
                "stream", false
        );

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + aiConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        System.out.println("HTTP Status: " + response.statusCode());
        System.out.println("Response Body: " + response.body());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("调用 DeepSeek 失败，HTTP " + response.statusCode() + "，响应内容：" + response.body());
        }

        JsonNode rootNode = objectMapper.readTree(response.body());
        return rootNode.path("choices").get(0).path("message").path("content").asText();
    }

    public String generateText(String prompt) throws IOException, InterruptedException {
        return AIGen(prompt);
    }
}