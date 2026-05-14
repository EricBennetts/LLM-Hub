package com.example.agent;

public record ModerationRequest(
        Long postId,
        Long userId,
        String title,
        String content
) {
}
