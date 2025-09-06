// File: src/main/java/com/example/service/impl/CommentServiceImpl.java
package com.example.service.impl;

import com.example.mapper.CommentMapper;
import com.example.pojo.Comment;
import com.example.service.CommentService;
import com.example.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;

    @Override
    public List<Comment> findByPostId(Long postId) {
        return commentMapper.findByPostId(postId);
    }

    @Override
    public void add(Comment comment) {
        Long currentUserId = UserContext.getUserId();
        comment.setUserId(currentUserId);
        commentMapper.insert(comment);
    }

    @Override
    public boolean update(Comment comment) {
        Long currentUserId = UserContext.getUserId();
        // 确保用户只能修改自己的评论
        Comment existingComment = commentMapper.findById(comment.getId());
        if (existingComment == null || !existingComment.getUserId().equals(currentUserId)) {
            return false;
        }
        comment.setUserId(currentUserId); // Mybatis的更新语句需要userId
        return commentMapper.update(comment) > 0;
    }

    @Override
    public boolean delete(Long id) {
        Long currentUserId = UserContext.getUserId();
        return commentMapper.delete(id, currentUserId) > 0;
    }
}