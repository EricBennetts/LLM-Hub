package com.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModerationTask implements Serializable {
    private Long postId;
    private Long userId; // captured at request time for WebSocket push and post ownership
    private String title;
    private String content;
}
