package com.example.agent;

public record ModerationResult(
        boolean approved,
        String reason,
        String model,
        String rawResponse,
        String toolCallsJson,
        Long latencyMs
) {
    public ModerationResult(boolean approved, String reason) {
        this(approved, reason, null, null, null, null);
    }
}
