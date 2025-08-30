package com.example.controller;

import com.example.pojo.Post;
import com.example.pojo.Result;
import com.example.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
@CrossOrigin
public class UserPostController {

    @Autowired
    private PostService postService;

    /**
     * 获取当前登录用户发布的所有帖子
     * @return 帖子列表
     */
    @GetMapping("/posts") // 完整路径将是 GET /user/posts
    public Result<List<Post>> getMyPosts() {
        List<Post> myPosts = postService.getPostsByCurrentUser();
        return Result.success(myPosts);
    }
}