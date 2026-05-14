package com.example.agent.eval;

import com.example.agent.ModerationAgent;
import com.example.agent.ModerationAgentException;
import com.example.agent.ModerationRequest;
import com.example.agent.ModerationResult;
import com.example.agent.client.DeepSeekChatCompletionClient;
import com.example.agent.tool.PlatformGuidelinesTool;
import com.example.agent.tool.UserModerationContextTool;
import com.example.config.AiConfig;
import com.example.mapper.ModerationLogMapper;
import com.example.pojo.ModerationLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ModerationLiveEvalTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path REPORT_PATH = Path.of("target", "moderation-eval-report.json");

    @Test
    @EnabledIfSystemProperty(named = "moderation.eval.live", matches = "true")
    void runLiveModerationEvalAgainstDeepSeek() throws Exception {
        String apiKey = readRequiredConfig("AI_DEEPSEEK_API_KEY", "ai.deepseek.api-key");
        String model = readConfig("AI_DEEPSEEK_MODEL", "ai.deepseek.model", "deepseek-chat");
        int limit = readIntConfig("MODERATION_EVAL_LIMIT", "moderation.eval.limit", Integer.MAX_VALUE);
        Set<String> caseIds = readCsvConfig("MODERATION_EVAL_CASE_IDS", "moderation.eval.case-ids");
        Long evalUserId = readLongConfig("MODERATION_EVAL_USER_ID", "moderation.eval.user-id", null);
        String syntheticUserHistory = readConfig(
                "MODERATION_EVAL_SYNTHETIC_USER_HISTORY",
                "moderation.eval.synthetic-user-history",
                "none"
        );

        List<ModerationEvalCase> cases = ModerationEvalDataset.loadCases();
        if (!caseIds.isEmpty()) {
            cases = cases.stream()
                    .filter(evalCase -> caseIds.contains(evalCase.id()))
                    .toList();
        }
        if (limit < cases.size()) {
            cases = cases.subList(0, limit);
        }
        assertFalse(cases.isEmpty(), "live eval requires at least one case");

        ModerationAgent moderationAgent = liveAgent(apiKey, model, syntheticUserHistory);
        Map<String, ModerationEvalPrediction> predictions = new LinkedHashMap<>();
        List<Map<String, Object>> caseReports = new ArrayList<>();

        for (ModerationEvalCase evalCase : cases) {
            long startedAt = System.nanoTime();
            try {
                ModerationResult result = moderationAgent.moderate(new ModerationRequest(
                        null,
                        evalUserId,
                        evalCase.title(),
                        evalCase.content()
                ));
                boolean platformToolCalled = result.toolCallsJson() != null
                        && result.toolCallsJson().contains(PlatformGuidelinesTool.NAME);
                boolean userContextToolCalled = result.toolCallsJson() != null
                        && result.toolCallsJson().contains(UserModerationContextTool.NAME);
                predictions.put(
                        evalCase.id(),
                        new ModerationEvalPrediction(result.decision(), result.latencyMs(), platformToolCalled, null)
                );
                caseReports.add(caseReport(evalCase, result, platformToolCalled, userContextToolCalled, null, null));
                printCaseResult(evalCase, result, platformToolCalled, userContextToolCalled, null);
            } catch (Exception e) {
                Long latencyMs = elapsedMs(startedAt);
                predictions.put(
                        evalCase.id(),
                        new ModerationEvalPrediction(null, latencyMs, false, errorType(e))
                );
                caseReports.add(caseReport(evalCase, null, false, false, errorType(e), e.getMessage()));
                printCaseResult(evalCase, null, false, false, e);
            }
        }

        ModerationEvalMetrics metrics = ModerationEvalMetrics.calculate(cases, predictions);
        writeReport(model, cases.size(), evalUserId, syntheticUserHistory, metrics, caseReports);

        System.out.println("Moderation live eval metrics: " + metrics.toReport());
        System.out.println("Moderation live eval report: " + REPORT_PATH.toAbsolutePath());
    }

    private static ModerationAgent liveAgent(String apiKey, String model, String syntheticUserHistory) {
        AiConfig aiConfig = new AiConfig();
        aiConfig.setApiKey(apiKey);
        aiConfig.setModel(model);

        DeepSeekChatCompletionClient client = new DeepSeekChatCompletionClient();
        ReflectionTestUtils.setField(client, "aiConfig", aiConfig);

        ModerationAgent agent = new ModerationAgent();
        ReflectionTestUtils.setField(agent, "aiConfig", aiConfig);
        ReflectionTestUtils.setField(agent, "platformGuidelinesTool", new PlatformGuidelinesTool());
        ReflectionTestUtils.setField(agent, "userModerationContextTool", userModerationContextTool(syntheticUserHistory));
        ReflectionTestUtils.setField(agent, "chatCompletionClient", client);
        return agent;
    }

    private static UserModerationContextTool userModerationContextTool(String syntheticUserHistory) {
        UserModerationContextTool tool = new UserModerationContextTool();
        ReflectionTestUtils.setField(
                tool,
                "moderationLogMapper",
                new LiveEvalModerationLogMapper(syntheticLogs(syntheticUserHistory))
        );
        return tool;
    }

    private static Map<String, Object> caseReport(ModerationEvalCase evalCase,
                                                  ModerationResult result,
                                                  boolean platformToolCalled,
                                                  boolean userContextToolCalled,
                                                  String errorType,
                                                  String errorMessage) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("id", evalCase.id());
        report.put("expectedDecision", evalCase.expectedDecision());
        report.put("acceptableDecisions", evalCase.acceptableDecisions());
        report.put("tags", evalCase.tags());
        report.put("passed", result != null && evalCase.accepts(result.decision()));
        report.put("actualDecision", result == null ? null : result.decision());
        report.put("actualRiskLevel", result == null ? null : result.riskLevel());
        report.put("categories", result == null ? List.of() : result.categories());
        report.put("confidence", result == null ? null : result.confidence());
        report.put("latencyMs", result == null ? null : result.latencyMs());
        report.put("toolCalled", platformToolCalled);
        report.put("platformGuidelinesToolCalled", platformToolCalled);
        report.put("userModerationContextToolCalled", userContextToolCalled);
        report.put("reason", result == null ? null : result.reason());
        report.put("errorType", errorType);
        report.put("errorMessage", errorMessage);
        return report;
    }

    private static void writeReport(String model,
                                    int caseCount,
                                    Long evalUserId,
                                    String syntheticUserHistory,
                                    ModerationEvalMetrics metrics,
                                    List<Map<String, Object>> caseReports) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runAt", Instant.now().toString());
        report.put("model", model);
        report.put("caseCount", caseCount);
        report.put("evalUserId", evalUserId);
        report.put("syntheticUserHistory", syntheticUserHistory);
        report.put("metrics", metrics.toMap());
        report.put("cases", caseReports);

        Files.createDirectories(REPORT_PATH.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);
    }

    private static void printCaseResult(ModerationEvalCase evalCase, ModerationResult result,
                                        boolean platformToolCalled, boolean userContextToolCalled,
                                        Exception exception) {
        if (result == null) {
            System.out.printf(
                    "[%s] expected=%s actual=ERROR error=%s%n",
                    evalCase.id(),
                    evalCase.expectedDecision(),
                    exception == null ? "unknown" : exception.getMessage()
            );
            return;
        }

        System.out.printf(
                "[%s] expected=%s actual=%s pass=%s tools=platform:%s,userContext:%s latencyMs=%s reason=%s%n",
                evalCase.id(),
                evalCase.expectedDecision(),
                result.decision(),
                evalCase.accepts(result.decision()),
                platformToolCalled,
                userContextToolCalled,
                result.latencyMs(),
                result.reason()
        );
    }

    private static String readRequiredConfig(String envName, String propertyName) {
        String value = readConfig(envName, propertyName, null);
        if (value == null || value.isBlank() || value.startsWith("Your-")) {
            throw new IllegalStateException(
                    "Missing DeepSeek API key. Set env " + envName + " or system property -D" + propertyName + "=..."
            );
        }
        return value;
    }

    private static String readConfig(String envName, String propertyName, String defaultValue) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property;
        }
        String env = System.getenv(envName);
        if (env != null && !env.isBlank()) {
            return env;
        }
        String localProperty = readLocalApplicationProperty(propertyName);
        if (localProperty != null && !localProperty.isBlank()) {
            return localProperty;
        }
        return defaultValue;
    }

    private static String readLocalApplicationProperty(String propertyName) {
        try (var inputStream = ModerationLiveEvalTest.class.getResourceAsStream("/application.properties")) {
            if (inputStream == null) {
                return null;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return properties.getProperty(propertyName);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int readIntConfig(String envName, String propertyName, int defaultValue) {
        String value = readConfig(envName, propertyName, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Integer.parseInt(value);
    }

    private static Long readLongConfig(String envName, String propertyName, Long defaultValue) {
        String value = readConfig(envName, propertyName, null);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private static Set<String> readCsvConfig(String envName, String propertyName) {
        String value = readConfig(envName, propertyName, "");
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Stream.of(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
    }

    private static Long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String errorType(Exception exception) {
        Throwable cause = exception instanceof ModerationAgentException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        return cause.getClass().getSimpleName();
    }

    private static List<ModerationLog> syntheticLogs(String syntheticUserHistory) {
        return switch (syntheticUserHistory == null ? "none" : syntheticUserHistory.trim().toLowerCase()) {
            case "spam" -> List.of(
                    moderationLog("REJECT", "广告引流，要求私聊联系方式"),
                    moderationLog("REJECT", "推广内容"),
                    moderationLog("REJECT", "疑似 spam"),
                    moderationLog("NEEDS_HUMAN_REVIEW", "上下文不足"),
                    moderationLog("APPROVE", "正常技术内容")
            );
            case "clean" -> List.of(
                    moderationLog("APPROVE", "正常技术内容"),
                    moderationLog("APPROVE", "正常讨论"),
                    moderationLog("APPROVE", "学习记录")
            );
            case "none" -> List.of();
            default -> throw new IllegalArgumentException(
                    "Unknown moderation.eval.synthetic-user-history: " + syntheticUserHistory
            );
        };
    }

    private static ModerationLog moderationLog(String decision, String reason) {
        ModerationLog log = new ModerationLog();
        log.setDecision(decision);
        log.setReason(reason);
        return log;
    }

    private record LiveEvalModerationLogMapper(List<ModerationLog> logs) implements ModerationLogMapper {

        @Override
        public int insert(ModerationLog moderationLog) {
            throw new UnsupportedOperationException("insert is not used by live eval");
        }

        @Override
        public List<ModerationLog> findByPostId(Long postId) {
            throw new UnsupportedOperationException("findByPostId is not used by live eval");
        }

        @Override
        public List<ModerationLog> findRecentByUserIdSince(Long userId, LocalDateTime since) {
            return logs;
        }
    }
}
