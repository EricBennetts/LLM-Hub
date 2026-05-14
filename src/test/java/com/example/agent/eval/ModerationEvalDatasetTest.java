package com.example.agent.eval;

import com.example.agent.ModerationDecision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationEvalDatasetTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("evalCases")
    void evalCaseShouldHaveValidLabels(ModerationEvalCase evalCase) {
        assertFalse(isBlank(evalCase.id()), "id is required");
        assertFalse(isBlank(evalCase.title()), evalCase.id() + " title is required");
        assertFalse(isBlank(evalCase.content()), evalCase.id() + " content is required");
        assertNotNull(evalCase.expectedDecision(), evalCase.id() + " expectedDecision is required");
        assertNotNull(evalCase.expectedRiskLevel(), evalCase.id() + " expectedRiskLevel is required");
        assertFalse(evalCase.acceptableDecisions().isEmpty(), evalCase.id() + " acceptableDecisions is required");
        assertTrue(
                evalCase.acceptableDecisions().contains(evalCase.expectedDecision()),
                evalCase.id() + " acceptableDecisions must contain expectedDecision"
        );
        assertFalse(evalCase.tags().isEmpty(), evalCase.id() + " tags are required");
        assertFalse(isBlank(evalCase.rationale()), evalCase.id() + " rationale is required");
    }

    @Test
    void evalDatasetShouldCoverCoreModerationBuckets(TestReporter reporter) throws IOException {
        List<ModerationEvalCase> cases = ModerationEvalDataset.loadCases();
        assertTrue(cases.size() >= 50, "moderation eval dataset should contain at least 50 cases");

        Set<String> ids = new HashSet<>();
        for (ModerationEvalCase evalCase : cases) {
            assertTrue(ids.add(evalCase.id()), "duplicate case id: " + evalCase.id());
        }

        Map<ModerationDecision, Long> decisionCounts = cases.stream()
                .collect(Collectors.groupingBy(
                        ModerationEvalCase::expectedDecision,
                        () -> new EnumMap<>(ModerationDecision.class),
                        Collectors.counting()
                ));
        Map<String, Long> tagCounts = cases.stream()
                .flatMap(evalCase -> evalCase.tags().stream())
                .collect(Collectors.groupingBy(tag -> tag, Collectors.counting()));

        assertTrue(decisionCounts.getOrDefault(ModerationDecision.APPROVE, 0L) >= 20,
                "dataset should cover enough approved content");
        assertTrue(decisionCounts.getOrDefault(ModerationDecision.REJECT, 0L) >= 15,
                "dataset should cover enough rejected content");
        assertTrue(decisionCounts.getOrDefault(ModerationDecision.NEEDS_HUMAN_REVIEW, 0L) >= 5,
                "dataset should cover enough human review content");
        assertTrue(tagCounts.getOrDefault("hard_case", 0L) >= 8,
                "dataset should include hard cases");
        assertTrue(tagCounts.getOrDefault("context_dependent", 0L) >= 4,
                "dataset should include context-dependent cases");
        assertTrue(tagCounts.containsKey("illegal"), "dataset should cover illegal content");
        assertTrue(tagCounts.containsKey("spam"), "dataset should cover spam content");
        assertTrue(tagCounts.containsKey("education"), "dataset should cover education context");

        reporter.publishEntry("caseCount", String.valueOf(cases.size()));
        reporter.publishEntry("expectedDecisionDistribution", decisionCounts.toString());
        reporter.publishEntry("topTags", tagCounts.toString());
    }

    @Test
    void metricsShouldCaptureAccuracyFalseKillsLeaksAndHumanReviewRate(TestReporter reporter) throws IOException {
        List<ModerationEvalCase> cases = ModerationEvalDataset.loadCases();
        Map<String, ModerationEvalPrediction> predictions = cases.stream()
                .collect(Collectors.toMap(
                        ModerationEvalCase::id,
                        evalCase -> new ModerationEvalPrediction(evalCase.expectedDecision(), 100L)
                ));

        predictions.put("normal-001", new ModerationEvalPrediction(ModerationDecision.REJECT, 100L));
        predictions.put("illegal-001", new ModerationEvalPrediction(ModerationDecision.APPROVE, 100L));
        predictions.put("spam-001", new ModerationEvalPrediction(ModerationDecision.NEEDS_HUMAN_REVIEW, 100L));

        ModerationEvalMetrics metrics = ModerationEvalMetrics.calculate(cases, predictions);
        long expectedApproveCount = count(cases, evalCase -> evalCase.expectedDecision() == ModerationDecision.APPROVE);
        long expectedRejectCount = count(cases, evalCase -> evalCase.expectedDecision() == ModerationDecision.REJECT);

        assertEquals(cases.size(), metrics.totalCases());
        assertEquals((cases.size() - 3.0) / cases.size(), metrics.overallAccuracy());
        assertEquals(1.0 / expectedApproveCount, metrics.falseKillRate());
        assertEquals(1.0 / expectedRejectCount, metrics.leakRate());
        assertEquals(6.0 / cases.size(), metrics.humanReviewRate());
        assertTrue(metrics.autoApprovePrecision() < 1.0);
        assertTrue(metrics.rejectPrecision() < 1.0);
        assertEquals(100.0, metrics.avgLatencyMs());

        reporter.publishEntry("moderationEvalMetrics", metrics.toReport());
    }

    private static Stream<ModerationEvalCase> evalCases() throws IOException {
        return ModerationEvalDataset.loadCases().stream();
    }

    private static long count(List<ModerationEvalCase> cases, Predicate<ModerationEvalCase> predicate) {
        return cases.stream().filter(predicate).count();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
