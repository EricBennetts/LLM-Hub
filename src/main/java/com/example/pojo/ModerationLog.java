package com.example.pojo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModerationLog {
    private Long id;
    private Long postId;
    private Long userId;
    private String decision;
    private String postStatus;
    private String reason;
    private String model;
    private Long latencyMs;
    private String toolCallsJson;
    private String rawResponse;
    private String errorType;
    private String errorMessage;
    private String titleSnapshot;
    private String contentPreview;
    private Integer contentLength;
    private LocalDateTime createTime;
}
