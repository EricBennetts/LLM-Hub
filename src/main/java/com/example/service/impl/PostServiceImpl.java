package com.example.service.impl;

import com.example.mapper.PostMapper;
import com.example.pojo.Post;
import com.example.service.PostService;
import com.example.utils.GenerateTextFromTextInput;
import com.example.utils.UserContext;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private GenerateTextFromTextInput aiGenerator;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    @Cacheable(value = "posts")
    public List<Post> getAllPosts() {
        System.out.println("--- 正在从数据库查询所有帖子 ---");
        return postMapper.findAllWithAuthor();
    }

    @Override
    @CacheEvict(value = "posts", allEntries = true)
    public void createPost(Post post) {
        // 从Spring Security 的上下文中获取当前登录用户
        Long userId = UserContext.getUserId();

        post.setUserId(userId);
        postMapper.insertPost(post);
    }

    @Override
    @Cacheable(value = "post_detail", key = "#id")
    public Post getPostById(Long id) {
        System.out.println("--- 正在从数据库查询帖子详情 ID: " + id + " ---");
        return postMapper.findById(id);
    }

    @Override
    public List<Post> getPostsByCurrentUser() {
        // 从 SecurityContextHolder 获取当前登录用户的信息
        Long currentUserId = UserContext.getUserId();

        // 调用 Mapper 方法，传入当前用户的ID
        return postMapper.findByUserId(currentUserId);
    }
    
    @Override
    @Caching(evict = {
            @CacheEvict(value = "posts", allEntries = true),
            @CacheEvict(value = "post_detail", key = "#post.id")
    })
    public boolean updatePost(Post post) {
        // 从 Spring Security 的上下文中获取当前登录用户
        Long currentUserId = UserContext.getUserId();
        
        // 确认帖子是否属于当前用户
        Post existingPost = postMapper.findByIdAndUserId(post.getId(), currentUserId);
        if (existingPost == null) {
            return false; // 帖子不存在或不属于当前用户
        }

        // 更新帖子
        int rowsAffected = postMapper.updatePost(post);
        String cacheKey = "post:ai_summary:" + post.getId();
        redisTemplate.delete(cacheKey);
        return rowsAffected > 0;
    }
    
    @Override
    @Caching(evict = {
            @CacheEvict(value = "posts", allEntries = true),
            @CacheEvict(value = "post_detail", key = "#postId")
    })
    public boolean deletePost(Long postId) {
        // 从 Spring Security 的上下文中获取当前登录用户
        Long currentUserId = UserContext.getUserId();
        
        // 删除帖子（只有帖子作者才能删除）
        int rowsAffected = postMapper.deleteByIdAndUserId(postId, currentUserId);
        return rowsAffected > 0;
    }

    @Override
    @CircuitBreaker(name = "googleAi", fallbackMethod = "aiSummaryFallback")
    public String generateAiSummary(Long postId) {
        // 1. 获取帖子内容
        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        String content = post.getContent();
        if (content == null || content.trim().length() < 10) {
            return "内容太短，无需总结。";
        }

        // 2. 构造 Prompt (提示词)
        // 截取一部分内容防止 token 溢出（假设限制 2000 字，可视模型情况调整）
        String contentToSummarize = content.length() > 2000 ? content.substring(0, 2000) : content;

        String prompt = "请用简洁的中文总结以下帖子的核心观点（50字以内）：\n\n" + contentToSummarize;

        // 3. 调用 AI 工具类
        try {
            // 使用你工具类中的实例方法
            return aiGenerator.generateText(prompt);
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("AI 服务暂时不可用，请稍后再试");
        }
    }

    public String aiSummaryFallback(Long postId, Throwable t) {
        System.err.println("触发AI熔断或降级，原因: " + t.getMessage());
        return "【系统提示】AI助手当前被限流，请稍后再试...";
    }
}