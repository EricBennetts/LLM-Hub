package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class EmailListener {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public EmailListener(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @RabbitListener(queues = "welcome.email.queue")
    public void sendWelcomeEmail(String email) {
        String idempotencyKey = "mq:idempotent:welcome_email:" + email;
        try {
            // 尝试在Redis中加锁
            Boolean isFirstTime = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", 30, TimeUnit.DAYS);
            if (Boolean.FALSE.equals(isFirstTime)) {
                // 如果是 false，说明 Redis 里已经有这个邮箱的记录了，这是重复消费
                System.out.println("【幂等拦截】检测到重复消息，已忽略发送欢迎邮件给: " + email);
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("欢迎注册LLM-Hub社区讨论平台！");
            message.setText("亲爱的用户，欢迎您注册我们的平台！祝您使用愉快！");

            mailSender.send(message);
            System.out.println("欢迎邮件已发送至: " + email);
        } catch (Exception e) {
            // 如果邮件发送失败了，必须把 Redis 里的 Key 删掉
            redisTemplate.delete(idempotencyKey);
            System.err.println("发送邮件失败: " + e.getMessage());
        }
    }
}
