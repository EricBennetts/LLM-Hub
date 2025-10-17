// File: src/main/java/com/example/service/impl/CommentServiceImpl.java
package com.example.service.impl;

import com.example.mapper.CommentMapper;
import com.example.mapper.PostMapper;
import com.example.pojo.Comment;
import com.example.pojo.Post;
import com.example.service.CommentService;
import com.example.utils.UserContext;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private PostMapper postMapper;

    @Override
    public List<Comment> findByPostId(Long postId) {
        return commentMapper.findByPostId(postId);
    }

    @Override
    public void add(Comment comment) {
        Long currentUserId = UserContext.getUserId();
        comment.setUserId(currentUserId);
        commentMapper.insert(comment);
        System.out.println("评论的id为" + comment.getId());
        // 添加评论后，发送消息给MQ，通知用户
        // 首先获取被评论的帖子，以得到帖子作者的ID
        Post commentedPost = postMapper.findById(comment.getPostId());
        // 如果帖子作者ID和当前用户ID不同，则发送消息给MQ
        if (commentedPost != null && !commentedPost.getUserId().equals(currentUserId)) {
            // 将评论对象作为消息发送到队列
            rabbitTemplate.convertAndSend("new.comment.queue", comment);
        }

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