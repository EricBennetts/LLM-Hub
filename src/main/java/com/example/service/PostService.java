package com.example.service;

import com.example.pojo.AiSummaryResult;
import com.example.pojo.Post;
import com.example.pojo.PostStatus;
import java.util.List;

public interface PostService {
    List<Post> getAllPosts();

    Post submitPostForReview(Post post);

    Post getPostById(Long id);

    Post getPostForModeration(Long id);

    List<Post> getPostsByCurrentUser();
    
    boolean updatePost(Post post);
    
    boolean deletePost(Long postId);

    boolean updatePostStatus(Long postId, PostStatus status);

    boolean completeModeration(Long postId, PostStatus status, String title, String content);

    AiSummaryResult generateAiSummary(Long postId);
}
