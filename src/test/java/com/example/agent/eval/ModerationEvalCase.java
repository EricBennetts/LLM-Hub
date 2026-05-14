package com.example.agent.eval;

import com.example.agent.ModerationDecision;
import com.example.agent.ModerationRiskLevel;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModerationEvalCase(
        String id,
        String title,
        String content,
        ModerationDecision expectedDecision,
        List<ModerationDecision> acceptableDecisions,
        ModerationRiskLevel expectedRiskLevel,
        List<String> tags,
        String rationale
) {
    public ModerationEvalCase {
        acceptableDecisions = acceptableDecisions == null ? List.of() : List.copyOf(acceptableDecisions);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public boolean accepts(ModerationDecision decision) {
        return acceptableDecisions.contains(decision);
    }

    @Override
    public String toString() {
        return id + " -> " + expectedDecision;
    }
}
