package com.example.listener;

import com.example.agent.ModerationAgent;
import com.example.agent.ModerationResult;
import com.example.config.RabbitMQConfig;
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

    @RabbitListener(queues = RabbitMQConfig.MODERATION_QUEUE, concurrency = "2-5")
    public void handleModerationTask(ModerationTask task) {
        Long userId = task.getUserId();
        Long postId = task.getPostId();
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "MODERATION");
        payload.put("postId", postId);

        if (postId == null) {
            payload.put("status", "ERROR");
            payload.put("message", "审核任务缺少帖子ID。");
            messagingTemplate.convertAndSend("/topic/user/" + userId + "/moderation", payload);
            return;
        }

        String title = task.getTitle();
        String content = task.getContent();

        try {
            if (title == null || content == null) {
                Post post = postService.getPostForModeration(postId);
                if (post == null) {
                    payload.put("status", "ERROR");
                    payload.put("message", "审核任务对应的帖子不存在。");
                    messagingTemplate.convertAndSend("/topic/user/" + userId + "/moderation", payload);
                    return;
                }
                title = post.getTitle();
                content = post.getContent();
            }

            ModerationResult result = moderationAgent.moderate(title, content);

            if (result.approved()) {
                if (postService.completeModeration(postId, PostStatus.PUBLISHED, title, content)) {
                    payload.put("status", "APPROVED");
                    payload.put("message", "Your post has been published.");
                } else {
                    putStalePayload(payload);
                }
            } else {
                if (postService.completeModeration(postId, PostStatus.REJECTED, title, content)) {
                    payload.put("status", "REJECTED");
                    payload.put("message", result.reason());
                } else {
                    putStalePayload(payload);
                }
            }
        } catch (Exception e) {
            if (title != null && content != null
                    && postService.completeModeration(postId, PostStatus.NEEDS_HUMAN_REVIEW, title, content)) {
                payload.put("status", "NEEDS_HUMAN_REVIEW");
                payload.put("message", "自动审核暂时无法完成，帖子已进入人工复核队列。");
            } else {
                putStalePayload(payload);
            }
        }

        messagingTemplate.convertAndSend("/topic/user/" + userId + "/moderation", payload);
    }

    private void putStalePayload(Map<String, Object> payload) {
        payload.put("status", "STALE");
        payload.put("message", "帖子内容已更新，旧审核任务已忽略。");
    }
}
