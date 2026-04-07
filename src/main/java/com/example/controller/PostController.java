package com.example.controller;

import com.example.annotation.AntiDuplicate;
import com.example.config.RabbitMQConfig;
import com.example.pojo.AiSummaryTask;
import com.example.pojo.Post;
import com.example.pojo.Result;
import com.example.service.PostService;
import com.example.utils.UserContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@CrossOrigin
public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 获取所有帖子 (GET /posts)
    @GetMapping
    public Result<List<Post>> list() {
        List<Post> posts = postService.getAllPosts();
        return Result.success(posts);
    }

    // 创建帖子 (POST /posts)
    @PostMapping
    @AntiDuplicate(time = 5)
    public Result create(@RequestBody Post post) {
        if (post.getTitle() == null || post.getTitle().trim().isEmpty() ||
                post.getContent() == null || post.getContent().trim().isEmpty()) {
            return Result.error("标题和内容不能为空");
        }

        postService.createPost(post);
        return Result.success();
    }

    // 获取帖子详情 (GET /posts/{id})
    @GetMapping("/{id}")
    public Result<Post> detail(@PathVariable Long id) {
        Post post = postService.getPostById(id);

        // 检查帖子是否存在
        if (post != null) {
            return Result.success(post);
        } else {
            return Result.error("帖子不存在");
        }
    }
    
    // 更新帖子 (PUT /posts/{id})
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Post post) {
        // 设置帖子ID
        post.setId(id);
        
        // 验证标题和内容
        if (post.getTitle() == null || post.getTitle().trim().isEmpty() ||
                post.getContent() == null || post.getContent().trim().isEmpty()) {
            return Result.error("标题和内容不能为空");
        }
        
        // 尝试更新帖子
        boolean success = postService.updatePost(post);
        if (success) {
            return Result.success();
        } else {
            return Result.error("帖子不存在或您无权修改此帖子");
        }
    }

    // 删除帖子 (DELETE /posts/{id})
    @DeleteMapping("/{id}")
    public Result delete(@PathVariable Long id) {
        // 尝试删除帖子
        boolean success = postService.deletePost(id);
        if (success) {
            return Result.success();
        } else {
            return Result.error("帖子不存在或您无权删除此帖子");
        }
    }

    @PostMapping("/{id}/ai-summary")
    @AntiDuplicate(time = 1)
    public Result<String> getAiSummary(@PathVariable Long id) {
        System.out.println("[AI] 进入 ai-summary 接口, postId=" + id);

        String cacheKey = "post:ai_summary:" + id;
        String cachedSummary = redisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedSummary)) {
            System.out.println("[AI] 命中缓存, postId=" + id);
            return Result.success(cachedSummary);
        }

        int currentQueueSize = rabbitTemplate.execute(channel ->
                channel.queueDeclarePassive(RabbitMQConfig.AI_SUMMARY_QUEUE).getMessageCount());

        System.out.println("[AI] 当前队列长度=" + currentQueueSize);

        if (currentQueueSize > 50) {
            return Result.error("当前排队人数较多，请稍后再试");
        }

        Long currentUserId = UserContext.getUserId();
        System.out.println("[AI] 当前用户ID=" + currentUserId);

        try {
            AiSummaryTask task = new AiSummaryTask(id, currentUserId);
            System.out.println("[AI] 准备发送MQ, task=" + task);
            rabbitTemplate.convertAndSend(RabbitMQConfig.AI_SUMMARY_QUEUE, task);
            System.out.println("[AI] MQ发送调用已完成");
            return Result.success("AI助手已开始阅读并总结，请留意页面通知...");
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("提交AI任务失败：" + e.getMessage());
        }
    }

}