package com.example.agent.tool;

import com.example.pojo.ModerationLog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserModerationContextToolTest {

    @Test
    void executeShouldReturnUnavailableWhenUserIdIsMissing() {
        UserModerationContextTool tool = new UserModerationContextTool();

        Map<String, Object> context = tool.execute(null);

        assertEquals(false, context.get("available"));
        assertEquals("userId is missing", context.get("reason"));
        assertEquals("Judge by current content and platform policy only.", context.get("guidance"));
    }

    @Test
    void buildContextShouldAggregateRecentModerationSignals() {
        UserModerationContextTool tool = new UserModerationContextTool();

        Map<String, Object> context = tool.buildContext(42L, List.of(
                log("REJECT", "广告引流，要求私聊联系方式"),
                log("REJECT", "推广内容"),
                log("REJECT", "疑似 spam"),
                log("NEEDS_HUMAN_REVIEW", "上下文不足"),
                log("APPROVE", "正常内容")
        ));

        assertEquals(true, context.get("available"));
        assertEquals(42L, context.get("userId"));
        assertEquals(5, context.get("recentModerationCount"));
        assertEquals(1, context.get("approvedCount"));
        assertEquals(3, context.get("rejectedCount"));
        assertEquals(1, context.get("humanReviewCount"));
        assertEquals(0.6, context.get("rejectionRate"));
        assertEquals(0.2, context.get("humanReviewRate"));
        assertEquals("HIGH", context.get("riskTier"));
        assertEquals("广告引流，要求私聊联系方式", context.get("lastRejectedReason"));

        @SuppressWarnings("unchecked")
        List<String> signals = (List<String>) context.get("dominantSignals");
        assertTrue(signals.contains("recent_rejections"));
        assertTrue(signals.contains("high_rejection_rate"));
        assertTrue(signals.contains("prior_spam_like_content"));
        assertFalse(signals.contains("prior_illegal_like_content"));
    }

    @Test
    void buildContextShouldReturnLowRiskForNoHistory() {
        UserModerationContextTool tool = new UserModerationContextTool();

        Map<String, Object> context = tool.buildContext(42L, List.of());

        assertEquals(true, context.get("available"));
        assertEquals(0, context.get("recentModerationCount"));
        assertEquals(0.0, context.get("rejectionRate"));
        assertEquals("LOW", context.get("riskTier"));
        assertEquals(List.of(), context.get("dominantSignals"));
        assertEquals("No prior moderation history. Judge primarily by current content and platform policy.",
                context.get("guidance"));
    }

    private ModerationLog log(String decision, String reason) {
        ModerationLog log = new ModerationLog();
        log.setDecision(decision);
        log.setReason(reason);
        return log;
    }
}
