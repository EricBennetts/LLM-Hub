package com.example.controller;

import com.example.pojo.Result;
import com.example.service.LikeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/posts/{postId}/like")
public class LikeController {

    @Autowired
    private LikeService likeService;

    @PostMapping
    public Result like(@PathVariable Long postId) {
        boolean success = likeService.likePost(postId);
        if (success) {
            return Result.success();
        }
        return Result.error("您已经点过赞了");
    }

    @DeleteMapping
    public Result unlike(@PathVariable Long postId) {
        boolean success = likeService.unlikePost(postId);
        if (success) {
            return Result.success();
        }
        return Result.error("您还没有点赞");
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status(@PathVariable Long postId) {
        Map<String, Object> data = new HashMap<>();
        data.put("liked", likeService.hasLikedPost(postId));
        data.put("likeCount", likeService.getPostLikeCount(postId));
        return Result.success(data);
    }
}