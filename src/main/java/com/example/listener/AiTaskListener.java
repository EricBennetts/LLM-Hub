package com.example.listener;

import com.example.config.RabbitMQConfig;
import com.example.pojo.AiSummaryTask;
import com.example.service.PostService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Component
public class AiTaskListener {
    @Autowired
    private PostService postService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @RabbitListener(queues = RabbitMQConfig.AI_SUMMARY_QUEUE, concurrency = "5-15")
    public void handleAiSummaryTask(AiSummaryTask task) {
        Long postId = task.getPostId();
        Long userId = task.getUserId();
        System.out.println("开始为用户 " + userId + " 异步生成帖子 " + postId + " 的AI总结...");
        HashMap<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("postId", postId);
        responsePayload.put("type", "AI_SUMMARY");
        try {
            String cacheKey = "post:ai_summary:" + postId;
            String summary = redisTemplate.opsForValue().get(cacheKey);
            if (!StringUtils.hasText(summary)) {
                // 真没有，才去调AI
                summary = postService.generateAiSummary(postId);
                // 存入缓存，保存10小时
                redisTemplate.opsForValue().set(cacheKey, summary, 10, TimeUnit.HOURS);
            }
            responsePayload.put("status", "SUCCESS");
            responsePayload.put("content", summary);
        } catch (Exception e) {
            System.out.println("AI生成失败: " + e.getMessage());
            responsePayload.put("status", "ERROR");
            responsePayload.put("content", "AI生成失败，请稍后重试。");
        }

        messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/ai", responsePayload);
        System.out.println("AI总结已推送到用户 " + userId + " 的 WebSocket");
    }
}
