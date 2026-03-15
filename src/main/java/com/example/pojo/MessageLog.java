package com.example.pojo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageLog {
    private String messageId;
    private String exchange;
    private String routingKey;
    private String content; // 存储发送的实际内容 (例如 email 地址的 JSON)
    private Integer status; // 0-投递中, 1-投递成功, 2-投递失败
    private Integer tryCount;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}