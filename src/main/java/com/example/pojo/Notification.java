package com.example.pojo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class Notification implements Serializable {

    private static final long serialVersionUID = 1L;

    // --- 数据库字段 ---
    private Long id;
    private Long recipientId; // 接收者ID (帖子作者)
    private Long senderId;    // 发送者ID (评论者)
    private Long postId;
    private Long commentId;
    private String type;      // e.g., "NEW_COMMENT"
    private String content;
    private boolean isRead;
    private LocalDateTime createTime;

    // --- 非数据库字段，用于API返回，提升前端体验 ---
    // 通过JOIN查询填充
    private String senderUsername;
    private String postTitle;
}