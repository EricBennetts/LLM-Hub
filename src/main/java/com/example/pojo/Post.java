package com.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private Long id;
    private String title;
    private String content;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // 为了方便前端显示作者信息，我们可以额外增加一个字段
    // 这个字段不属于 post 表，但可以通过数据库连接查询填充
    private String authorUsername;
}