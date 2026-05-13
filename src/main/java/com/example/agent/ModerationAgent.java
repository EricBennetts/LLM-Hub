package com.example.agent;

import com.example.agent.tool.PlatformGuidelinesTool;
import com.example.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ModerationAgent {

    @Autowired
    private AiConfig aiConfig;

    @Autowired
    private PlatformGuidelinesTool platformGuidelinesTool;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // Tool definition sent to the AI so it knows what it can call
    private static final Map<String, Object> TOOL_DEFINITIONS = Map.of(
            "type", "function",
            "function", Map.of(
                    "name", PlatformGuidelinesTool.NAME,
                    "description", PlatformGuidelinesTool.DESCRIPTION,
                    "parameters", Map.of("type", "object", "properties", Map.of())
            )
    );

    public ModerationResult moderate(String title, String content) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "You are a content moderation agent. Use the getPlatformGuidelines tool to retrieve the platform rules, " +
                "then decide if the post is approved or rejected. " +
                "Respond ONLY with valid JSON: {\"approved\": true/false, \"reason\": \"...\"}"));
        messages.add(Map.of("role", "user", "content",
                "Please moderate this post.\nTitle: " + title + "\nContent: " + content));

        // Tool use loop — runs until the AI gives a final text response
        for (int i = 0; i < 5; i++) {
            JsonNode response = callDeepSeek(messages);
            JsonNode choice = response.path("choices").get(0);
            String finishReason = choice.path("finish_reason").asText();
            JsonNode message = choice.path("message");

            // Add assistant message to history
            messages.add(objectMapper.convertValue(message, Map.class));

            if ("tool_calls".equals(finishReason)) {
                // AI wants to call a tool — execute it and feed result back
                JsonNode toolCalls = message.path("tool_calls");
                for (JsonNode toolCall : toolCalls) {
                    String toolName = toolCall.path("function").path("name").asText();
                    String toolCallId = toolCall.path("id").asText();
                    Object result = executeTool(toolName);
                    messages.add(Map.of(
                            "role", "tool",
                            "tool_call_id", toolCallId,
                            "content", objectMapper.writeValueAsString(result)
                    ));
                }
            } else {
                // AI gave a final answer — parse it
                String text = message.path("content").asText().trim();
                // Strip markdown code fences if present
                if (text.startsWith("```")) {
                    text = text.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
                }
                JsonNode parsed = objectMapper.readTree(text);
                return new ModerationResult(
                        parsed.path("approved").asBoolean(),
                        parsed.path("reason").asText()
                );
            }
        }

        // Fallback: approve if agent loop didn't resolve (fail open)
        return new ModerationResult(true, "Moderation inconclusive, approved by default.");
    }

    private Object executeTool(String toolName) {
        if (PlatformGuidelinesTool.NAME.equals(toolName)) {
            return platformGuidelinesTool.execute();
        }
        throw new IllegalArgumentException("Unknown tool: " + toolName);
    }

    private JsonNode callDeepSeek(List<Map<String, Object>> messages) throws Exception {
        Map<String, Object> body = Map.of(
                "model", aiConfig.getModel(),
                "messages", messages,
                "tools", List.of(TOOL_DEFINITIONS),
                "tool_choice", "auto"
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("Authorization", "Bearer " + aiConfig.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("DeepSeek API error " + response.statusCode() + ": " + response.body());
        }

        return objectMapper.readTree(response.body());
    }
}
