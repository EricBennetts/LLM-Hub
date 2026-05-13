package com.example.agent;

public class ModerationAgentException extends Exception {
    private final String model;
    private final String rawResponse;
    private final String toolCallsJson;
    private final Long latencyMs;

    public ModerationAgentException(String message, Throwable cause, String model,
                                    String rawResponse, String toolCallsJson, Long latencyMs) {
        super(message, cause);
        this.model = model;
        this.rawResponse = rawResponse;
        this.toolCallsJson = toolCallsJson;
        this.latencyMs = latencyMs;
    }

    public String getModel() {
        return model;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public String getToolCallsJson() {
        return toolCallsJson;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }
}
