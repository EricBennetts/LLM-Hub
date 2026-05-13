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
import java.util.LinkedHashMap;
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
        long startedAt = System.nanoTime();
        String model = aiConfig.getModel();
        String rawResponse = null;
        List<Map<String, Object>> toolCallLogs = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "You are a content moderation agent. Use the getPlatformGuidelines tool to retrieve the platform rules, " +
                "then decide if the post is approved or rejected. " +
                "Respond ONLY with one valid JSON object and no prose before or after it: " +
                "{\"approved\": true/false, \"reason\": \"...\"}"));
        messages.add(Map.of("role", "user", "content",
                "Please moderate this post.\nTitle: " + title + "\nContent: " + content));

        try {
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
                        String arguments = toolCall.path("function").path("arguments").asText();
                        Object result = executeTool(toolName);

                        Map<String, Object> toolCallLog = new LinkedHashMap<>();
                        toolCallLog.put("id", toolCallId);
                        toolCallLog.put("name", toolName);
                        toolCallLog.put("arguments", arguments);
                        toolCallLog.put("result", result);
                        toolCallLogs.add(toolCallLog);

                        messages.add(Map.of(
                                "role", "tool",
                                "tool_call_id", toolCallId,
                                "content", objectMapper.writeValueAsString(result)
                        ));
                    }
                } else {
                    // AI gave a final answer — parse it
                    String text = message.path("content").asText().trim();
                    rawResponse = text;
                    // Strip markdown code fences if present
                    if (text.startsWith("```")) {
                        text = text.replaceAll("```[a-z]*\\n?", "").replace("```", "").trim();
                    }
                    try {
                        JsonNode parsed = objectMapper.readTree(extractJsonObject(text));
                        return new ModerationResult(
                                parsed.path("approved").asBoolean(),
                                parsed.path("reason").asText(),
                                model,
                                rawResponse,
                                objectMapper.writeValueAsString(toolCallLogs),
                                elapsedMs(startedAt)
                        );
                    } catch (Exception e) {
                        throw new ModerationAgentException(
                                "Moderation agent returned invalid JSON",
                                e,
                                model,
                                rawResponse,
                                objectMapper.writeValueAsString(toolCallLogs),
                                elapsedMs(startedAt)
                        );
                    }
                }
            }

            throw new ModerationAgentException(
                    "Moderation agent did not produce a final answer within the tool loop limit",
                    null,
                    model,
                    rawResponse,
                    objectMapper.writeValueAsString(toolCallLogs),
                    elapsedMs(startedAt)
            );
        } catch (ModerationAgentException e) {
            throw e;
        } catch (Exception e) {
            throw new ModerationAgentException(
                    "Moderation agent failed",
                    e,
                    model,
                    rawResponse,
                    objectMapper.writeValueAsString(toolCallLogs),
                    elapsedMs(startedAt)
            );
        }
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

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Moderation response is empty");
        }

        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("Moderation response does not contain a JSON object");
        }

        boolean inString = false;
        boolean escaped = false;
        int depth = 0;

        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return trimmed.substring(start, i + 1);
                }
            }
        }

        throw new IllegalArgumentException("Moderation response contains an incomplete JSON object");
    }
}
