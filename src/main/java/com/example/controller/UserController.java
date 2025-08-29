package com.example.controller;

import com.example.pojo.Result;
import com.example.pojo.User;
import com.example.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@CrossOrigin
public class UserController {

    @Autowired
    private UserService userService;

    // 注册方法
    @PostMapping("/register")
    public Result register(@RequestBody User user) {
        try {
            userService.addOneUser(user);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    // 新增：处理登录请求
    @PostMapping("/login")
    public Result login(@RequestBody User user) {
        try {
            String token = userService.login(user);
            return Result.success(token);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}