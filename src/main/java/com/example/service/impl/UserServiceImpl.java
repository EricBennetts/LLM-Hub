package com.example.service.impl;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.mapper.MessageLogMapper;
import com.example.mapper.UserMapper;
import com.example.pojo.MessageLog;
import com.example.pojo.User;
import com.example.service.UserService;
import com.example.utils.JwtUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private MessageLogMapper messageLogMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addOneUser(User user) {
        // 1. 检查用户名和邮箱是否被占用
        User foundByUsername = userMapper.findByUsername(user.getUsername());
        if (foundByUsername != null) {
            throw new RuntimeException("用户名 '" + user.getUsername() + "' 已被占用");
        }
        User foundByEmail = userMapper.findByEmail(user.getEmail());
        if (foundByEmail != null) {
            throw new RuntimeException("电子邮箱 '" + user.getEmail() + "' 已被注册");
        }

        // 2. 插入用户数据
        int result = userMapper.insertUser(user);
        if (result > 0) {
            try {
                // 3. 构建本地消息表记录
                String messageId = UUID.randomUUID().toString();
                String queueName = "welcome.email.queue"; // 使用默认交换机，routingKey就是队列名
                MessageLog log = new MessageLog();
                log.setMessageId(messageId);
                log.setExchange(""); // Default exchanger
                log.setRoutingKey(queueName);
                log.setContent(objectMapper.writeValueAsString(user.getEmail()));
                log.setStatus(0);
                log.setTryCount(0);
                log.setNextRetryTime(LocalDateTime.now().plusSeconds(30)); // 30s后第一次重试
                // 4. 消息落库
                messageLogMapper.insert(log);

                // 5. 发送消息给 RabbitMQ，并带上 CorrelationData
                CorrelationData correlationData = new CorrelationData(messageId);
                rabbitTemplate.convertAndSend("", queueName, user.getEmail(), correlationData);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("注册失败，构建欢迎邮件异常: " + e.getMessage());
            }
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

    @Override
    public void logout(String token) {
        DecodedJWT decodedJWT = JWT.decode(token);
        Date expiresAt = decodedJWT.getExpiresAt();

        long remainingMillis = expiresAt.getTime() - System.currentTimeMillis();
        if (remainingMillis > 0) {
            stringRedisTemplate.opsForValue().set(
                    "jwt:blacklist:" + token,
                    "1",
                    remainingMillis,
                    TimeUnit.MILLISECONDS);
        }
    }
}