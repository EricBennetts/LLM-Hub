package com.example.service;

public interface LikeService {
    boolean likePost(Long postId);
    boolean unlikePost(Long postId);
    boolean hasLikedPost(Long postId);
    int getPostLikeCount(Long postId);
}