package com.example.agent.eval;

import com.example.agent.ModerationDecision;

public record ModerationEvalPrediction(
        ModerationDecision decision,
        Long latencyMs,
        boolean toolCalled,
        String errorType
) {
    public ModerationEvalPrediction(ModerationDecision decision, Long latencyMs) {
        this(decision, latencyMs, false, null);
    }
}
