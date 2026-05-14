package com.example.agent;

import com.example.agent.client.ChatCompletionClient;
import com.example.agent.tool.PlatformGuidelinesTool;
import com.example.config.AiConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ModerationAgentTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void moderateShouldExecuteGuidelinesToolAndParseFinalDecision() throws Exception {
        FakeChatCompletionClient client = new FakeChatCompletionClient(
                toolCallResponse("call-guidelines-1", PlatformGuidelinesTool.NAME),
                finalTextResponse("""
                        {"decision":"APPROVE","riskLevel":"LOW","categories":[],"confidence":0.91,"reason":"正常技术讨论"}
                        """)
        );
        ModerationAgent agent = agentWith(client);

        ModerationResult result = agent.moderate("缓存问题", "Redis 缓存更新后如何清理详情缓存？");

        assertEquals(ModerationDecision.APPROVE, result.decision());
        assertEquals(ModerationRiskLevel.LOW, result.riskLevel());
        assertEquals(0.91, result.confidence());
        assertEquals("正常技术讨论", result.reason());
        assertEquals("deepseek-test", result.model());
        assertTrue(result.toolCallsJson().contains(PlatformGuidelinesTool.NAME));
        assertTrue(result.toolCallsJson().contains("prohibited"));
        assertEquals(2, client.calls().size());

        Map<String, Object> firstToolDefinition = client.calls().get(0).tools().get(0);
        assertEquals("function", firstToolDefinition.get("type"));

        List<Map<String, Object>> secondCallMessages = client.calls().get(1).messages();
        assertTrue(secondCallMessages.stream().anyMatch(message -> "tool".equals(message.get("role"))));
    }

    @Test
    void moderateShouldParseMarkdownWrappedJson() throws Exception {
        FakeChatCompletionClient client = new FakeChatCompletionClient(
                finalTextResponse("""
                        ```json
                        {"decision":"NEEDS_HUMAN_REVIEW","riskLevel":"MEDIUM","categories":["other"],"confidence":"0.42","reason":"上下文不足"}
                        ```
                        """)
        );
        ModerationAgent agent = agentWith(client);

        ModerationResult result = agent.moderate("含糊内容", "公开不方便说，懂的人私聊。");

        assertEquals(ModerationDecision.NEEDS_HUMAN_REVIEW, result.decision());
        assertEquals(ModerationRiskLevel.MEDIUM, result.riskLevel());
        assertEquals(List.of("other"), result.categories());
        assertEquals(0.42, result.confidence());
        assertEquals("上下文不足", result.reason());
    }

    @Test
    void moderateShouldRemainCompatibleWithLegacyApprovedFlag() throws Exception {
        FakeChatCompletionClient client = new FakeChatCompletionClient(
                finalTextResponse("""
                        {"approved":false,"reason":"明显广告引流"}
                        """)
        );
        ModerationAgent agent = agentWith(client);

        ModerationResult result = agent.moderate("限时推广", "加我领取福利，私聊联系方式。");

        assertEquals(ModerationDecision.REJECT, result.decision());
        assertEquals(ModerationRiskLevel.HIGH, result.riskLevel());
        assertEquals(0.8, result.confidence());
        assertEquals("明显广告引流", result.reason());
    }

    @Test
    void moderateShouldWrapInvalidJsonResponse() throws Exception {
        FakeChatCompletionClient client = new FakeChatCompletionClient(
                finalTextResponse("I think this post is fine.")
        );
        ModerationAgent agent = agentWith(client);

        try {
            agent.moderate("普通帖子", "正常内容");
            fail("Expected ModerationAgentException");
        } catch (ModerationAgentException e) {
            assertEquals("Moderation agent returned invalid JSON", e.getMessage());
            assertEquals("deepseek-test", e.getModel());
            assertEquals("I think this post is fine.", e.getRawResponse());
            assertInstanceOf(IllegalArgumentException.class, e.getCause());
        }
    }

    @Test
    void moderateShouldFailWhenToolLoopDoesNotProduceFinalAnswer() throws Exception {
        FakeChatCompletionClient client = new FakeChatCompletionClient(
                toolCallResponse("call-1", PlatformGuidelinesTool.NAME),
                toolCallResponse("call-2", PlatformGuidelinesTool.NAME),
                toolCallResponse("call-3", PlatformGuidelinesTool.NAME),
                toolCallResponse("call-4", PlatformGuidelinesTool.NAME),
                toolCallResponse("call-5", PlatformGuidelinesTool.NAME)
        );
        ModerationAgent agent = agentWith(client);

        try {
            agent.moderate("循环调用", "模型一直要求调用工具。");
            fail("Expected ModerationAgentException");
        } catch (ModerationAgentException e) {
            assertEquals("Moderation agent did not produce a final answer within the tool loop limit", e.getMessage());
            assertEquals("deepseek-test", e.getModel());
            assertFalse(e.getToolCallsJson().isBlank());
        }

        assertEquals(5, client.calls().size());
    }

    private static ModerationAgent agentWith(ChatCompletionClient client) {
        AiConfig aiConfig = new AiConfig();
        aiConfig.setModel("deepseek-test");
        aiConfig.setApiKey("test-api-key");

        ModerationAgent agent = new ModerationAgent();
        ReflectionTestUtils.setField(agent, "aiConfig", aiConfig);
        ReflectionTestUtils.setField(agent, "platformGuidelinesTool", new PlatformGuidelinesTool());
        ReflectionTestUtils.setField(agent, "chatCompletionClient", client);
        return agent;
    }

    private static JsonNode toolCallResponse(String id, String toolName) throws Exception {
        return OBJECT_MAPPER.readTree("""
                {
                  "choices": [
                    {
                      "finish_reason": "tool_calls",
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "%s",
                            "type": "function",
                            "function": {
                              "name": "%s",
                              "arguments": "{}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(id, toolName));
    }

    private static JsonNode finalTextResponse(String text) throws Exception {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode choice = root.putArray("choices").addObject();
        choice.put("finish_reason", "stop");

        ObjectNode message = choice.putObject("message");
        message.put("role", "assistant");
        message.put("content", text);
        return root;
    }

    private static class FakeChatCompletionClient implements ChatCompletionClient {

        private final Queue<JsonNode> responses = new ArrayDeque<>();
        private final List<ClientCall> calls = new ArrayList<>();

        private FakeChatCompletionClient(JsonNode... responses) {
            this.responses.addAll(List.of(responses));
        }

        @Override
        public JsonNode complete(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
            calls.add(new ClientCall(copy(messages), copy(tools)));
            if (responses.isEmpty()) {
                throw new AssertionError("No fake response configured");
            }
            return responses.remove();
        }

        private List<ClientCall> calls() {
            return calls;
        }

        @SuppressWarnings("unchecked")
        private static List<Map<String, Object>> copy(List<Map<String, Object>> value) {
            return OBJECT_MAPPER.convertValue(value, List.class);
        }
    }

    private record ClientCall(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
    }
}
