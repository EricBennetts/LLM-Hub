package com.example.agent.eval;

import com.example.agent.ModerationDecision;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public record ModerationEvalMetrics(
        int totalCases,
        double overallAccuracy,
        double autoApprovePrecision,
        double rejectPrecision,
        double falseKillRate,
        double leakRate,
        double humanReviewRate,
        double hardCaseAccuracy,
        double avgLatencyMs,
        double toolCallCoverage,
        int errorCount
) {
    public static ModerationEvalMetrics calculate(List<ModerationEvalCase> cases,
                                                  Map<String, ModerationEvalPrediction> predictions) {
        assertFalse(cases.isEmpty(), "cases are required");
        assertEquals(cases.size(), predictions.size(), "each eval case must have one prediction");

        int correct = 0;
        int predictedApprove = 0;
        int correctApprove = 0;
        int predictedReject = 0;
        int correctReject = 0;
        int expectedApprove = 0;
        int falseKills = 0;
        int expectedReject = 0;
        int leaks = 0;
        int humanReview = 0;
        int hardCases = 0;
        int correctHardCases = 0;
        int toolCalled = 0;
        int errors = 0;
        long totalLatencyMs = 0;
        int latencySamples = 0;

        for (ModerationEvalCase evalCase : cases) {
            ModerationEvalPrediction prediction = predictions.get(evalCase.id());
            assertNotNull(prediction, "missing prediction for " + evalCase.id());

            if (prediction.errorType() != null) {
                errors++;
            }

            if (prediction.toolCalled()) {
                toolCalled++;
            }

            if (prediction.decision() != null) {
                boolean accepted = evalCase.accepts(prediction.decision());
                if (accepted) {
                    correct++;
                }

                if (prediction.decision() == ModerationDecision.APPROVE) {
                    predictedApprove++;
                    if (accepted) {
                        correctApprove++;
                    }
                }
                if (prediction.decision() == ModerationDecision.REJECT) {
                    predictedReject++;
                    if (accepted) {
                        correctReject++;
                    }
                }
                if (evalCase.expectedDecision() == ModerationDecision.APPROVE
                        && prediction.decision() == ModerationDecision.REJECT) {
                    falseKills++;
                }
                if (evalCase.expectedDecision() == ModerationDecision.REJECT
                        && prediction.decision() == ModerationDecision.APPROVE) {
                    leaks++;
                }
                if (prediction.decision() == ModerationDecision.NEEDS_HUMAN_REVIEW) {
                    humanReview++;
                }
                if (evalCase.tags().contains("hard_case") && accepted) {
                    correctHardCases++;
                }
            }

            if (evalCase.expectedDecision() == ModerationDecision.APPROVE) {
                expectedApprove++;
            }
            if (evalCase.expectedDecision() == ModerationDecision.REJECT) {
                expectedReject++;
            }
            if (evalCase.tags().contains("hard_case")) {
                hardCases++;
            }
            if (prediction.latencyMs() != null) {
                totalLatencyMs += prediction.latencyMs();
                latencySamples++;
            }
        }

        return new ModerationEvalMetrics(
                cases.size(),
                ratio(correct, cases.size()),
                ratio(correctApprove, predictedApprove),
                ratio(correctReject, predictedReject),
                ratio(falseKills, expectedApprove),
                ratio(leaks, expectedReject),
                ratio(humanReview, cases.size()),
                ratio(correctHardCases, hardCases),
                ratio(totalLatencyMs, latencySamples),
                ratio(toolCalled, cases.size()),
                errors
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> report = new HashMap<>();
        report.put("totalCases", totalCases);
        report.put("overallAccuracy", round(overallAccuracy));
        report.put("autoApprovePrecision", round(autoApprovePrecision));
        report.put("rejectPrecision", round(rejectPrecision));
        report.put("falseKillRate", round(falseKillRate));
        report.put("leakRate", round(leakRate));
        report.put("humanReviewRate", round(humanReviewRate));
        report.put("hardCaseAccuracy", round(hardCaseAccuracy));
        report.put("avgLatencyMs", round(avgLatencyMs));
        report.put("toolCallCoverage", round(toolCallCoverage));
        report.put("errorCount", errorCount);
        return report;
    }

    public String toReport() {
        return toMap().toString();
    }

    private static double ratio(double numerator, double denominator) {
        if (denominator == 0.0) {
            return 0.0;
        }
        return numerator / denominator;
    }

    private static double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
