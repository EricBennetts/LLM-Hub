package com.example.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String WELCOME_EMAIL_QUEUE = "welcome.email.queue";
    public static final String NEW_COMMENT_QUEUE = "new.comment.queue";

    public static final String AI_SUMMARY_QUEUE = "ai.summary.queue";
    @Bean
    public Queue welcomeEmailQueue() {
        return new Queue(WELCOME_EMAIL_QUEUE, true); // true表示队列持久化
    }

    @Bean
    public Queue newCommentQueue() {
        return new Queue(NEW_COMMENT_QUEUE, true);
    }

    @Bean
    public Queue aiSummaryQueue() {
        return new Queue(AI_SUMMARY_QUEUE, true);
    }
}
