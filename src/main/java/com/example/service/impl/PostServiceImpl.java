package com.example.service.impl;

import com.example.mapper.PostMapper;
import com.example.pojo.Post;
import com.example.service.PostService;
import com.example.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
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
    public List<Post> getAllPosts() {
        return postMapper.findAllWithAuthor();
    }

    @Override
    public void createPost(Post post) {
        // 从Spring Security 的上下文中获取当前登录用户
        Long userId = UserContext.getUserId();

        post.setUserId(userId);
        postMapper.insertPost(post);
    }

    @Override
    public Post getPostById(Long id) {
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
    public boolean deletePost(Long postId) {
        // 从 Spring Security 的上下文中获取当前登录用户
        Long currentUserId = UserContext.getUserId();
        
        // 删除帖子（只有帖子作者才能删除）
        int rowsAffected = postMapper.deleteByIdAndUserId(postId, currentUserId);
        return rowsAffected > 0;
    }
}