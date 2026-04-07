package com.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSummaryResult {
    /**
     * SUCCESS: 正常生成
     * FALLBACK: 降级结果（如限流、熔断）
     */
    private String status;

    /**
     * 总结内容或提示文案
     */
    private String content;

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFallback() {
        return "FALLBACK".equals(status);
    }
}