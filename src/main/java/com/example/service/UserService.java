package com.example.service;

import com.example.pojo.User;

public interface UserService {
    int addOneUser(User user);

    // 用户登录接口
    String login(User user);
}