package com.example.controller;

import com.example.pojo.Comment;
import com.example.pojo.Result;
import com.example.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping
public class CommentController {

    @Autowired
    private CommentService commentService;

    // 获取帖子的所有评论
    @GetMapping("/posts/{postId}/comments")
    public Result<List<Comment>> getCommentsByPostId(@PathVariable Long postId) {
        List<Comment> comments = commentService.findByPostId(postId);
        return Result.success(comments);
    }

    // 在帖子上添加新评论
    @PostMapping("/posts/{postId}/comments")
    public Result addComment(@PathVariable Long postId, @RequestBody Comment comment) {
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return Result.error("评论内容不能为空");
        }
        comment.setPostId(postId);
        commentService.add(comment);
        return Result.success();
    }

    // 更新评论
    @PutMapping("/comments/{id}")
    public Result updateComment(@PathVariable Long id, @RequestBody Comment comment) {
        if (comment.getContent() == null || comment.getContent().trim().isEmpty()) {
            return Result.error("评论内容不能为空");
        }
        comment.setId(id);
        boolean success = commentService.update(comment);
        if (success) {
            return Result.success();
        } else {
            return Result.error("评论不存在或您无权修改");
        }
    }

    // 删除评论
    @DeleteMapping("/comments/{id}")
    public Result deleteComment(@PathVariable Long id) {
        boolean success = commentService.delete(id);
        if (success) {
            return Result.success();
        } else {
            return Result.error("评论不存在或您无权删除");
        }
    }
}