package com.example.agent.tool;

import com.example.mapper.ModerationLogMapper;
import com.example.pojo.ModerationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class UserModerationContextTool {

    public static final String NAME = "getUserModerationContext";
    public static final String DESCRIPTION =
            "Returns aggregated recent moderation context for the post author. " +
            "Use this only as a risk signal for ambiguous or low-confidence cases; " +
            "do not reject content solely because of user history.";

    private static final int WINDOW_DAYS = 30;

    @Autowired
    private ModerationLogMapper moderationLogMapper;

    public Map<String, Object> execute(Long userId) {
        if (userId == null) {
            Map<String, Object> missing = new LinkedHashMap<>();
            missing.put("available", false);
            missing.put("reason", "userId is missing");
            missing.put("guidance", "Judge by current content and platform policy only.");
            return missing;
        }

        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<ModerationLog> logs = moderationLogMapper.findRecentByUserIdSince(userId, since);
        return buildContext(userId, logs);
    }

    Map<String, Object> buildContext(Long userId, List<ModerationLog> logs) {
        List<ModerationLog> safeLogs = logs == null ? List.of() : logs;
        int approvedCount = 0;
        int rejectedCount = 0;
        int humanReviewCount = 0;
        int staleCount = 0;
        int errorCount = 0;
        String lastRejectedReason = null;

        for (ModerationLog log : safeLogs) {
            String decision = normalize(log.getDecision());
            if ("APPROVE".equals(decision) || "APPROVED".equals(decision)) {
                approvedCount++;
            } else if ("REJECT".equals(decision) || "REJECTED".equals(decision)) {
                rejectedCount++;
                if (lastRejectedReason == null) {
                    lastRejectedReason = log.getReason();
                }
            } else if ("NEEDS_HUMAN_REVIEW".equals(decision)) {
                humanReviewCount++;
            } else if ("STALE".equals(decision)) {
                staleCount++;
            } else if ("ERROR".equals(decision)) {
                errorCount++;
            }
        }

        int recentModerationCount = safeLogs.size();
        double rejectionRate = ratio(rejectedCount, recentModerationCount);
        double humanReviewRate = ratio(humanReviewCount, recentModerationCount);
        List<String> dominantSignals = dominantSignals(
                recentModerationCount,
                rejectedCount,
                humanReviewCount,
                rejectionRate,
                lastRejectedReason
        );

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("available", true);
        context.put("userId", userId);
        context.put("windowDays", WINDOW_DAYS);
        context.put("recentModerationCount", recentModerationCount);
        context.put("approvedCount", approvedCount);
        context.put("rejectedCount", rejectedCount);
        context.put("humanReviewCount", humanReviewCount);
        context.put("staleCount", staleCount);
        context.put("errorCount", errorCount);
        context.put("rejectionRate", round(rejectionRate));
        context.put("humanReviewRate", round(humanReviewRate));
        context.put("riskTier", riskTier(recentModerationCount, rejectedCount, humanReviewCount, rejectionRate));
        context.put("dominantSignals", dominantSignals);
        context.put("lastRejectedReason", lastRejectedReason);
        context.put("guidance", guidance(recentModerationCount));
        return context;
    }

    private String riskTier(int recentModerationCount, int rejectedCount, int humanReviewCount, double rejectionRate) {
        if (rejectedCount >= 3 || humanReviewCount >= 3
                || (recentModerationCount >= 4 && rejectionRate >= 0.5)) {
            return "HIGH";
        }
        if (rejectedCount >= 1 || humanReviewCount >= 1
                || (recentModerationCount >= 4 && rejectionRate >= 0.25)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private List<String> dominantSignals(int recentModerationCount, int rejectedCount, int humanReviewCount,
                                         double rejectionRate, String lastRejectedReason) {
        List<String> signals = new ArrayList<>();
        if (rejectedCount >= 3) {
            signals.add("recent_rejections");
        }
        if (humanReviewCount >= 2) {
            signals.add("frequent_human_review");
        }
        if (recentModerationCount >= 4 && rejectionRate >= 0.5) {
            signals.add("high_rejection_rate");
        }
        String reason = lastRejectedReason == null ? "" : lastRejectedReason.toLowerCase(Locale.ROOT);
        if (containsAny(reason, "广告", "私聊", "联系方式", "推广", "spam")) {
            signals.add("prior_spam_like_content");
        }
        if (containsAny(reason, "违法", "证件", "数据", "博彩", "盗版", "illegal", "fraud")) {
            signals.add("prior_illegal_like_content");
        }
        return signals;
    }

    private boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private String guidance(int recentModerationCount) {
        if (recentModerationCount == 0) {
            return "No prior moderation history. Judge primarily by current content and platform policy.";
        }
        return "Use this context only for ambiguous or low-confidence cases. Do not reject solely because of user history.";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private double ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        return (double) numerator / denominator;
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
