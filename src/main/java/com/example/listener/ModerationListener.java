package com.example.listener;

import com.example.agent.ModerationAgent;
import com.example.agent.ModerationAgentException;
import com.example.agent.ModerationDecision;
import com.example.agent.ModerationRequest;
import com.example.agent.ModerationResult;
import com.example.config.RabbitMQConfig;
import com.example.mapper.ModerationLogMapper;
import com.example.pojo.ModerationLog;
import com.example.pojo.ModerationTask;
import com.example.pojo.Post;
import com.example.pojo.PostStatus;
import com.example.service.PostService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ModerationListener {

    @Autowired
    private ModerationAgent moderationAgent;

    @Autowired
    private PostService postService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ModerationLogMapper moderationLogMapper;

    @RabbitListener(queues = RabbitMQConfig.MODERATION_QUEUE, concurrency = "2-5")
    public void handleModerationTask(ModerationTask task) {
        Long userId = task.getUserId();
        Long postId = task.getPostId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MODERATION");
        payload.put("postId", postId);

        String title = task.getTitle();
        String content = task.getContent();

        if (postId == null) {
            payload.put("status", "ERROR");
            payload.put("message", "审核任务缺少帖子ID。");
            saveLog(null, userId, "ERROR", null, "审核任务缺少帖子ID。", null,
                    "INVALID_TASK", "postId is null", title, content);
            messagingTemplate.convertAndSend("/topic/user/" + userId + "/moderation", payload);
            return;
        }

        try {
            if (title == null || content == null) {
                Post post = postService.getPostForModeration(postId);
                if (post == null) {
                    payload.put("status", "ERROR");
                    payload.put("message", "审核任务对应的帖子不存在。");
                    saveLog(postId, userId, "ERROR", null, "审核任务对应的帖子不存在。", null,
                            "POST_NOT_FOUND", "Post does not exist", title, content);
                    messagingTemplate.convertAndSend("/topic/user/" + userId + "/moderation", payload);
                    return;
                }
                title = post.getTitle();
                content = post.getContent();
            }

            ModerationResult result = moderationAgent.moderate(new ModerationRequest(postId, userId, title, content));
            PostStatus targetStatus = postStatusForDecision(result.decision());

            if (postService.completeModeration(postId, targetStatus, title, content)) {
                payload.put("status", payloadStatusForDecision(result.decision()));
                payload.put("message", messageForDecision(result));
                saveLog(postId, userId, result.decision().name(), targetStatus.name(), result.reason(), result,
                        null, null, title, content);
            } else {
                putStalePayload(payload);
                saveLog(postId, userId, "STALE", null, "帖子内容已更新，旧审核任务已忽略。", result,
                        null, null, title, content);
            }
        } catch (Exception e) {
            if (title != null && content != null
                    && postService.completeModeration(postId, PostStatus.NEEDS_HUMAN_REVIEW, title, content)) {
                payload.put("status", "NEEDS_HUMAN_REVIEW");
                payload.put("message", "自动审核暂时无法完成，帖子已进入人工复核队列。");
                saveLog(postId, userId, "NEEDS_HUMAN_REVIEW", PostStatus.NEEDS_HUMAN_REVIEW.name(),
                        "自动审核异常，进入人工复核。", e, title, content);
            } else {
                putStalePayload(payload);
                saveLog(postId, userId, "STALE", null, "帖子内容已更新，旧审核异常结果已忽略。", e, title, content);
            }
        }

        messagingTemplate.convertAndSend("/topic/user/" + userId + "/moderation", payload);
    }

    private void putStalePayload(Map<String, Object> payload) {
        payload.put("status", "STALE");
        payload.put("message", "帖子内容已更新，旧审核任务已忽略。");
    }

    private PostStatus postStatusForDecision(ModerationDecision decision) {
        return switch (decision) {
            case APPROVE -> PostStatus.PUBLISHED;
            case REJECT -> PostStatus.REJECTED;
            case NEEDS_HUMAN_REVIEW -> PostStatus.NEEDS_HUMAN_REVIEW;
        };
    }

    private String payloadStatusForDecision(ModerationDecision decision) {
        return switch (decision) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case NEEDS_HUMAN_REVIEW -> "NEEDS_HUMAN_REVIEW";
        };
    }

    private String messageForDecision(ModerationResult result) {
        return switch (result.decision()) {
            case APPROVE -> "Your post has been published.";
            case REJECT -> result.reason();
            case NEEDS_HUMAN_REVIEW -> result.reason().isBlank()
                    ? "帖子需要人工复核。"
                    : "帖子需要人工复核：" + result.reason();
        };
    }

    private void saveLog(Long postId, Long userId, String decision, String postStatus, String reason,
                         ModerationResult result, String errorType, String errorMessage,
                         String title, String content) {
        ModerationLog log = baseLog(postId, userId, decision, postStatus, reason, title, content);
        if (result != null) {
            log.setModel(result.model());
            log.setLatencyMs(result.latencyMs());
            log.setToolCallsJson(result.toolCallsJson());
            log.setRawResponse(result.rawResponse());
        }
        log.setErrorType(errorType);
        log.setErrorMessage(errorMessage);
        insertLogSafely(log);
    }

    private void saveLog(Long postId, Long userId, String decision, String postStatus, String reason,
                         Exception exception, String title, String content) {
        ModerationLog log = baseLog(postId, userId, decision, postStatus, reason, title, content);
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        log.setErrorType(cause.getClass().getSimpleName());
        log.setErrorMessage(errorMessage(exception, cause));

        if (exception instanceof ModerationAgentException agentException) {
            log.setModel(agentException.getModel());
            log.setLatencyMs(agentException.getLatencyMs());
            log.setToolCallsJson(agentException.getToolCallsJson());
            log.setRawResponse(agentException.getRawResponse());
        }

        insertLogSafely(log);
    }

    private ModerationLog baseLog(Long postId, Long userId, String decision, String postStatus,
                                  String reason, String title, String content) {
        ModerationLog log = new ModerationLog();
        log.setPostId(postId);
        log.setUserId(userId);
        log.setDecision(decision);
        log.setPostStatus(postStatus);
        log.setReason(reason);
        log.setTitleSnapshot(truncate(title, 255));
        log.setContentPreview(truncate(content, 512));
        log.setContentLength(content == null ? null : content.length());
        return log;
    }

    private void insertLogSafely(ModerationLog log) {
        try {
            moderationLogMapper.insert(log);
        } catch (Exception e) {
            System.err.println("保存审核日志失败: " + e.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String errorMessage(Exception exception, Throwable cause) {
        String message = exception.getMessage();
        String causeMessage = cause.getMessage();
        if (cause == exception || causeMessage == null || causeMessage.equals(message)) {
            return message;
        }
        return message + ": " + causeMessage;
    }
}
