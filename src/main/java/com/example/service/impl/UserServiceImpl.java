package com.example.service.impl;

import com.example.mapper.UserMapper;
import com.example.pojo.User;
import com.example.service.UserService;
import com.example.utils.JwtUtil;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public int addOneUser(User user) {
        // 检查用户名和邮箱是否被占用 (逻辑不变)
        User foundByUsername = userMapper.findByUsername(user.getUsername());
        if (foundByUsername != null) {
            throw new RuntimeException("用户名 '" + user.getUsername() + "' 已被占用");
        }
        User foundByEmail = userMapper.findByEmail(user.getEmail());
        if (foundByEmail != null) {
            throw new RuntimeException("电子邮箱 '" + user.getEmail() + "' 已被注册");
        }

        int result = userMapper.insertUser(user);
        if (result > 0) {
            // 将消息发送到RabbitMQ消息队列中
            // 第一个参数是目标队列的名称
            // 第二个参数是消息内容
            rabbitTemplate.convertAndSend("welcome.email.queue", user.getEmail());
        }
        return result;
    }

    @Override
    public String login(User user) {
        // 1. 根据用户名查询用户
        User loginUser = userMapper.findByUsername(user.getUsername());

        // 2. 判断用户是否存在
        if (loginUser == null) {
            throw new RuntimeException("用户名不存在");
        }

        // 3. 直接比较明文字符串
        if (!user.getPassword().equals(loginUser.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        // 4. 登录成功, 生成JWT令牌 (逻辑不变)
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", loginUser.getId());
        claims.put("username", loginUser.getUsername());

        return JwtUtil.genToken(claims);
    }
}