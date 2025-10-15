package com.example.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailListener {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public EmailListener(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @RabbitListener(queues = "welcome.email.queue")
    public void sendWelcomeEmail(String email) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(email);
            message.setSubject("欢迎注册LLM-Hub社区讨论平台！");
            message.setText("亲爱的用户，欢迎您注册我们的平台！祝您使用愉快！");

            mailSender.send(message);
            System.out.println("欢迎邮件已发送至: " + email);
        } catch (Exception e) {
            System.err.println("发送邮件失败: " + e.getMessage());
        }
    }
}
