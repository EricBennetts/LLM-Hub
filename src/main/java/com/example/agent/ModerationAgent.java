package com.example.agent;

import com.example.agent.client.ChatCompletionClient;
import com.example.agent.tool.PlatformGuidelinesTool;
import com.example.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    @Autowired
    private ChatCompletionClient chatCompletionClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Tool definition sent to the AI so it knows what it can call
    private static final Map<String, Object> TOOL_DEFINITION = Map.of(
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
                "then decide whether the post should be approved, rejected, or sent to human review. " +
                "Use NEEDS_HUMAN_REVIEW when the content is ambiguous, context-dependent, or your confidence is low. " +
                "The categories field should contain zero or more of: spam, harassment, hate, sexual, violence, misinformation, illegal, other. " +
                "Respond ONLY with one valid JSON object and no prose before or after it: " +
                "{\"decision\":\"APPROVE|REJECT|NEEDS_HUMAN_REVIEW\", \"riskLevel\":\"LOW|MEDIUM|HIGH\", " +
                "\"categories\":[\"spam\"], " +
                "\"confidence\":0.0, \"reason\":\"...\"}"));
        messages.add(Map.of("role", "user", "content",
                "Please moderate this post.\nTitle: " + title + "\nContent: " + content));

        try {
            // Tool use loop — runs until the AI gives a final text response
            for (int i = 0; i < 5; i++) {
                JsonNode response = chatCompletionClient.complete(messages, List.of(TOOL_DEFINITION));
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
                        return parseModerationResult(
                                parsed,
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

    private ModerationResult parseModerationResult(JsonNode parsed, String model, String rawResponse,
                                                   String toolCallsJson, Long latencyMs) {
        ModerationDecision decision = parseDecision(parsed);
        ModerationRiskLevel riskLevel = parseRiskLevel(parsed.path("riskLevel").asText(null), decision);
        List<String> categories = parseCategories(parsed.path("categories"));
        double confidence = parseConfidence(parsed.path("confidence"), decision);
        String reason = parsed.path("reason").asText("");

        return new ModerationResult(
                decision,
                riskLevel,
                categories,
                confidence,
                reason,
                model,
                rawResponse,
                toolCallsJson,
                latencyMs
        );
    }

    private ModerationDecision parseDecision(JsonNode parsed) {
        String value = parsed.path("decision").asText(null);
        if (value == null || value.isBlank()) {
            if (parsed.has("approved")) {
                return parsed.path("approved").asBoolean()
                        ? ModerationDecision.APPROVE
                        : ModerationDecision.REJECT;
            }
            throw new IllegalArgumentException("Moderation response is missing decision");
        }

        String normalized = value.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "APPROVE", "APPROVED" -> ModerationDecision.APPROVE;
            case "REJECT", "REJECTED" -> ModerationDecision.REJECT;
            case "NEEDS_HUMAN_REVIEW", "HUMAN_REVIEW", "NEEDS_REVIEW" -> ModerationDecision.NEEDS_HUMAN_REVIEW;
            default -> throw new IllegalArgumentException("Unknown moderation decision: " + value);
        };
    }

    private ModerationRiskLevel parseRiskLevel(String value, ModerationDecision decision) {
        if (value == null || value.isBlank()) {
            return switch (decision) {
                case APPROVE -> ModerationRiskLevel.LOW;
                case REJECT -> ModerationRiskLevel.HIGH;
                case NEEDS_HUMAN_REVIEW -> ModerationRiskLevel.MEDIUM;
            };
        }

        String normalized = value.trim().toUpperCase().replace('-', '_');
        return switch (normalized) {
            case "LOW" -> ModerationRiskLevel.LOW;
            case "MEDIUM", "MID" -> ModerationRiskLevel.MEDIUM;
            case "HIGH" -> ModerationRiskLevel.HIGH;
            default -> throw new IllegalArgumentException("Unknown moderation risk level: " + value);
        };
    }

    private List<String> parseCategories(JsonNode categoriesNode) {
        if (categoriesNode == null || categoriesNode.isMissingNode() || categoriesNode.isNull()) {
            return List.of();
        }

        List<String> categories = new ArrayList<>();
        if (categoriesNode.isArray()) {
            for (JsonNode node : categoriesNode) {
                String category = node.asText("").trim();
                if (!category.isBlank()) {
                    categories.add(category);
                }
            }
        } else {
            String category = categoriesNode.asText("").trim();
            if (!category.isBlank()) {
                categories.add(category);
            }
        }
        return categories;
    }

    private double parseConfidence(JsonNode confidenceNode, ModerationDecision decision) {
        if (confidenceNode != null && confidenceNode.isNumber()) {
            return confidenceNode.asDouble();
        }
        if (confidenceNode != null && confidenceNode.isTextual()) {
            try {
                return Double.parseDouble(confidenceNode.asText());
            } catch (NumberFormatException ignored) {
                // Fall through to the decision-based default.
            }
        }
        return decision == ModerationDecision.NEEDS_HUMAN_REVIEW ? 0.4 : 0.8;
    }
}
