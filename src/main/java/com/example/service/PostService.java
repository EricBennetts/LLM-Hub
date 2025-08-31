package com.example.service;

import com.example.pojo.Post;
import java.util.List;

public interface PostService {
    List<Post> getAllPosts();

    void createPost(Post post);

    Post getPostById(Long id);

    List<Post> getPostsByCurrentUser();
    
    boolean updatePost(Post post);
}