package com.example.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PlatformGuidelinesTool {

    public static final String NAME = "getPlatformGuidelines";
    public static final String DESCRIPTION = "Returns the platform's content moderation guidelines. Call this to understand what content is allowed or prohibited before making a moderation decision.";

    public Map<String, Object> execute() {
        return Map.of(
            "prohibited", List.of(
                "Hate speech or discrimination based on race, gender, religion, or nationality",
                "Explicit sexual content or nudity",
                "Graphic violence or gore",
                "Harassment, threats, or doxxing of individuals",
                "Spam or repetitive promotional content",
                "Misinformation or deliberately false claims",
                "Illegal activity promotion (drugs, weapons, fraud)"
            ),
            "allowed", List.of(
                "Constructive debate and differing opinions",
                "Educational content including sensitive historical topics",
                "Satire and humor that does not target individuals",
                "Personal experiences and storytelling"
            ),
            "note", "When in doubt, consider context. Educational or journalistic content discussing prohibited topics is generally allowed."
        );
    }
}
