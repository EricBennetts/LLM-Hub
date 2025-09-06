package com.example.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Comment {
    private Long id;
    private String content;
    private Long userId;
    private Long postId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 非数据库字段，用于连接查询后显示作者用户名
    private String authorUsername;
}