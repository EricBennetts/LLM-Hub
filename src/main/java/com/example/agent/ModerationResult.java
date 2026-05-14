package com.example.agent;

import java.util.List;

public record ModerationResult(
        ModerationDecision decision,
        ModerationRiskLevel riskLevel,
        List<String> categories,
        double confidence,
        String reason,
        String model,
        String rawResponse,
        String toolCallsJson,
        Long latencyMs
) {
    public ModerationResult {
        if (decision == null) {
            decision = ModerationDecision.NEEDS_HUMAN_REVIEW;
        }
        if (riskLevel == null) {
            riskLevel = defaultRiskLevel(decision);
        }
        categories = categories == null ? List.of() : List.copyOf(categories);
        confidence = Math.max(0.0, Math.min(1.0, confidence));
        reason = reason == null ? "" : reason;
    }

    public ModerationResult(boolean approved, String reason) {
        this(
                approved ? ModerationDecision.APPROVE : ModerationDecision.REJECT,
                approved ? ModerationRiskLevel.LOW : ModerationRiskLevel.HIGH,
                List.of(),
                0.8,
                reason,
                null,
                null,
                null,
                null
        );
    }

    public boolean approved() {
        return decision == ModerationDecision.APPROVE;
    }

    private static ModerationRiskLevel defaultRiskLevel(ModerationDecision decision) {
        return switch (decision) {
            case APPROVE -> ModerationRiskLevel.LOW;
            case REJECT -> ModerationRiskLevel.HIGH;
            case NEEDS_HUMAN_REVIEW -> ModerationRiskLevel.MEDIUM;
        };
    }
}
