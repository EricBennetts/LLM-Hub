package com.example.service;

import com.example.pojo.Comment;
import java.util.List;

public interface CommentService {
    List<Comment> findByPostId(Long postId);
    void add(Comment comment);
    boolean update(Comment comment);
    boolean delete(Long id);
}