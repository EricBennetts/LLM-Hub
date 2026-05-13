package com.example.controller;

import com.example.mapper.ModerationLogMapper;
import com.example.mapper.PostMapper;
import com.example.pojo.ModerationLog;
import com.example.pojo.Post;
import com.example.pojo.Result;
import com.example.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/posts/{postId}/moderation-logs")
public class ModerationLogController {

    @Autowired
    private ModerationLogMapper moderationLogMapper;

    @Autowired
    private PostMapper postMapper;

    @GetMapping
    public Result<List<ModerationLog>> list(@PathVariable Long postId) {
        Long userId = UserContext.getUserId();
        Post post = postMapper.findByIdAndUserId(postId, userId);
        if (post == null) {
            return Result.error("帖子不存在或您无权查看审核记录");
        }
        return Result.success(moderationLogMapper.findByPostId(postId));
    }
}
