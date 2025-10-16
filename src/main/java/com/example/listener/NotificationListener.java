// File: src/main/java/com/example/listener/NotificationListener.java
package com.example.listener;

import com.example.pojo.Comment;
import com.example.service.NotificationService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class NotificationListener {

    @Autowired
    private NotificationService notificationService;

    // 这个监听器是整个通知流程的起点
    @RabbitListener(queues = "new.comment.queue")
    public void handleNewComment(Comment comment) {
        try {
            System.out.println("RabbitMQ收到新评论消息，准备调用通知服务。Comment ID: " + comment.getId());
            // 直接调用Service的核心方法，将业务逻辑委托给它
            notificationService.createAndPushNotificationForNewComment(comment);
        } catch (Exception e) {
            System.err.println("处理新评论通知时发生错误: " + e.getMessage());
            e.printStackTrace();
            // 在这里可以添加错误处理逻辑，比如将失败的消息存入死信队列
        }
    }
}