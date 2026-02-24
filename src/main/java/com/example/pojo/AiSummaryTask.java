package com.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSummaryTask implements Serializable {
    private Long postId;
    private Long userId; // 必须带上请求者的ID，用于后续WebSocket定向推送
}