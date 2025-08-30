package com.example.service.impl;

import com.example.mapper.PostMapper;
import com.example.pojo.Post;
import com.example.service.PostService;
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 获取之前存入的claims
        Map<String, Object> claims = (Map<String, Object>)authentication.getPrincipal();
        Number userIdNumber = (Number) claims.get("id");
        long userId = userIdNumber.longValue();

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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
        Number userIdNumber = (Number) claims.get("id");
        Long currentUserId = userIdNumber.longValue();

        // 调用 Mapper 方法，传入当前用户的ID
        return postMapper.findByUserId(currentUserId);
    }
}