package com.example.agent.eval;

import com.example.agent.ModerationAgent;
import com.example.agent.ModerationAgentException;
import com.example.agent.ModerationResult;
import com.example.agent.client.DeepSeekChatCompletionClient;
import com.example.agent.tool.PlatformGuidelinesTool;
import com.example.config.AiConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

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

        List<ModerationEvalCase> cases = ModerationEvalDataset.loadCases();
        if (limit < cases.size()) {
            cases = cases.subList(0, limit);
        }
        assertFalse(cases.isEmpty(), "live eval requires at least one case");

        ModerationAgent moderationAgent = liveAgent(apiKey, model);
        Map<String, ModerationEvalPrediction> predictions = new LinkedHashMap<>();
        List<Map<String, Object>> caseReports = new ArrayList<>();

        for (ModerationEvalCase evalCase : cases) {
            long startedAt = System.nanoTime();
            try {
                ModerationResult result = moderationAgent.moderate(evalCase.title(), evalCase.content());
                boolean toolCalled = result.toolCallsJson() != null
                        && result.toolCallsJson().contains(PlatformGuidelinesTool.NAME);
                predictions.put(
                        evalCase.id(),
                        new ModerationEvalPrediction(result.decision(), result.latencyMs(), toolCalled, null)
                );
                caseReports.add(caseReport(evalCase, result, toolCalled, null, null));
                printCaseResult(evalCase, result, null);
            } catch (Exception e) {
                Long latencyMs = elapsedMs(startedAt);
                predictions.put(
                        evalCase.id(),
                        new ModerationEvalPrediction(null, latencyMs, false, errorType(e))
                );
                caseReports.add(caseReport(evalCase, null, false, errorType(e), e.getMessage()));
                printCaseResult(evalCase, null, e);
            }
        }

        ModerationEvalMetrics metrics = ModerationEvalMetrics.calculate(cases, predictions);
        writeReport(model, cases.size(), metrics, caseReports);

        System.out.println("Moderation live eval metrics: " + metrics.toReport());
        System.out.println("Moderation live eval report: " + REPORT_PATH.toAbsolutePath());
    }

    private static ModerationAgent liveAgent(String apiKey, String model) {
        AiConfig aiConfig = new AiConfig();
        aiConfig.setApiKey(apiKey);
        aiConfig.setModel(model);

        DeepSeekChatCompletionClient client = new DeepSeekChatCompletionClient();
        ReflectionTestUtils.setField(client, "aiConfig", aiConfig);

        ModerationAgent agent = new ModerationAgent();
        ReflectionTestUtils.setField(agent, "aiConfig", aiConfig);
        ReflectionTestUtils.setField(agent, "platformGuidelinesTool", new PlatformGuidelinesTool());
        ReflectionTestUtils.setField(agent, "chatCompletionClient", client);
        return agent;
    }

    private static Map<String, Object> caseReport(ModerationEvalCase evalCase,
                                                  ModerationResult result,
                                                  boolean toolCalled,
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
        report.put("toolCalled", toolCalled);
        report.put("reason", result == null ? null : result.reason());
        report.put("errorType", errorType);
        report.put("errorMessage", errorMessage);
        return report;
    }

    private static void writeReport(String model,
                                    int caseCount,
                                    ModerationEvalMetrics metrics,
                                    List<Map<String, Object>> caseReports) throws Exception {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runAt", Instant.now().toString());
        report.put("model", model);
        report.put("caseCount", caseCount);
        report.put("metrics", metrics.toMap());
        report.put("cases", caseReports);

        Files.createDirectories(REPORT_PATH.getParent());
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(REPORT_PATH.toFile(), report);
    }

    private static void printCaseResult(ModerationEvalCase evalCase, ModerationResult result, Exception exception) {
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
                "[%s] expected=%s actual=%s pass=%s latencyMs=%s reason=%s%n",
                evalCase.id(),
                evalCase.expectedDecision(),
                result.decision(),
                evalCase.accepts(result.decision()),
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

    private static Long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static String errorType(Exception exception) {
        Throwable cause = exception instanceof ModerationAgentException && exception.getCause() != null
                ? exception.getCause()
                : exception;
        return cause.getClass().getSimpleName();
    }
}
