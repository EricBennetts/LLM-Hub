package com.example.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String WELCOME_EMAIL_QUEUE = "welcome.email.queue";

    @Bean
    public Queue welcomeEmailQueue() {
        // 若RabbitMQ中不存在一个名为"welcome.email.queue"的队列则创建一个名为 "welcome.email.queue" 的队列
        return new Queue(WELCOME_EMAIL_QUEUE, true); // true表示队列持久化
    }
}