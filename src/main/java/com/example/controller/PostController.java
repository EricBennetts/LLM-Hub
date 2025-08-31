package com.example.controller;

import com.example.pojo.Post;
import com.example.pojo.Result;
import com.example.service.PostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/posts")
@CrossOrigin
public class PostController {

    @Autowired
    private PostService postService;

    // 获取所有帖子 (GET /posts)
    @GetMapping
    public Result<List<Post>> list() {
        List<Post> posts = postService.getAllPosts();
        return Result.success(posts);
    }

    // 创建帖子 (POST /posts)
    @PostMapping
    public Result create(@RequestBody Post post) {
        if (post.getTitle() == null || post.getTitle().trim().isEmpty() ||
                post.getContent() == null || post.getContent().trim().isEmpty()) {
            return Result.error("标题和内容不能为空");
        }

        postService.createPost(post);
        return Result.success();
    }

    // 获取帖子详情 (GET /posts/{id})
    @GetMapping("/{id}")
    public Result<Post> detail(@PathVariable Long id) {
        Post post = postService.getPostById(id);

        // 检查帖子是否存在
        if (post != null) {
            return Result.success(post);
        } else {
            return Result.error("帖子不存在");
        }
    }
    
    // 更新帖子 (PUT /posts/{id})
    @PutMapping("/{id}")
    public Result update(@PathVariable Long id, @RequestBody Post post) {
        // 设置帖子ID
        post.setId(id);
        
        // 验证标题和内容
        if (post.getTitle() == null || post.getTitle().trim().isEmpty() ||
                post.getContent() == null || post.getContent().trim().isEmpty()) {
            return Result.error("标题和内容不能为空");
        }
        
        // 尝试更新帖子
        boolean success = postService.updatePost(post);
        if (success) {
            return Result.success();
        } else {
            return Result.error("帖子不存在或您无权修改此帖子");
        }
    }

}