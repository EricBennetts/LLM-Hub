package com.example.service.impl;

import com.example.mapper.PostLikeMapper;
import com.example.mapper.PostMapper;
import com.example.pojo.Post;
import com.example.pojo.PostLike;
import com.example.service.LikeService;
import com.example.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeServiceImpl implements LikeService {

    @Autowired
    private PostLikeMapper postLikeMapper;

    @Autowired
    private PostMapper postMapper;

    @Override
    @Caching(evict = {
            @CacheEvict(value = "posts", allEntries = true),
            @CacheEvict(value = "post_detail", key = "#postId")
    })
    @Transactional
    public boolean likePost(Long postId) {
        Long userId = UserContext.getUserId();

        Post post = postMapper.findById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }

        PostLike postLike = new PostLike();
        postLike.setPostId(postId);
        postLike.setUserId(userId);

        try {
            postLikeMapper.insert(postLike);   // 先插入
            postLikeMapper.increaseLikeCount(postId); // 插入成功再加1
            return true;
        } catch (DuplicateKeyException e) {
            return false; // 已点赞
        }
    }

    @Override
    @Caching(evict = {
            @CacheEvict(value = "posts", allEntries = true),
            @CacheEvict(value = "post_detail", key = "#postId")
    })
    @Transactional
    public boolean unlikePost(Long postId) {
        Long userId = UserContext.getUserId();

        if (postLikeMapper.exists(postId, userId) == 0) {
            return false; // 本来就没点过
        }

        int rows = postLikeMapper.delete(postId, userId);
        if (rows > 0) {
            postLikeMapper.decreaseLikeCount(postId);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasLikedPost(Long postId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return false;
        }
        return postLikeMapper.exists(postId, userId) > 0;
    }

    @Override
    public int getPostLikeCount(Long postId) {
        Post post = postMapper.findById(postId);
        return post == null || post.getLikeCount() == null ? 0 : post.getLikeCount();
    }
}