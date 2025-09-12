package com.example.service.impl;

import com.example.mapper.PostMapper;
import com.example.pojo.Post;
import com.example.service.PostService;
import com.example.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostMapper postMapper;

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
}