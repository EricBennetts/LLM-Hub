package com.example.service.impl;

import com.example.mapper.NotificationMapper;
import com.example.mapper.PostMapper;
import com.example.mapper.UserMapper;
import com.example.pojo.Comment;
import com.example.pojo.Notification;
import com.example.pojo.Post;
import com.example.pojo.User;
import com.example.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private NotificationMapper notificationMapper;

    @Autowired
    private PostMapper postMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Override
    public void createAndPushNotificationForNewComment(Comment comment) {
        Post post = postMapper.findById(comment.getPostId());
        User sender = userMapper.findById(comment.getUserId());

        if (post == null || sender == null) {
            System.err.println("无法创建通知：帖子或发送者不存在！！");
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(post.getUserId());
        notification.setSenderId(sender.getId());
        notification.setPostId(post.getId());
        notification.setCommentId(comment.getId());
        notification.setType("NEW_COMMENT");

        String contentSummary = comment.getContent();
        if (contentSummary.length() > 50) {
            contentSummary = contentSummary.substring(0, 50) + "...";
        }
        notification.setContent(sender.getUsername() + " 评论了你的帖子 \"" + post.getTitle() + "\": " + contentSummary);
        notification.setRead(false);
        notificationMapper.insert(notification);

        // 通过 WebSocket 将新通知推送到指定用户

        System.out.println("准备推送通知给用户: " + post.getUserId());
        // 执行convertAndSendToUser后，消息就已经转换为JSON并发送往目标用户了
        // 从convertAndSendToUser名字即可得知，目的地应该是一个用户专属的，会在前面加上/user前缀
        // 最终的目标路径应该是/user/{userId}/queue/notifications
        messagingTemplate.convertAndSendToUser(
                post.getUserId().toString(),      // 目标用户的ID (必须是字符串)
                "/queue/notifications",           // 目标路径 (客户端订阅的路径)
                notification                      // 推送的内容 (会自动序列化为JSON)
        );
        System.out.println("通知推送完成。");
    }

    @Override
    public List<Notification> findByRecipientId(Long recipientId) {
        return notificationMapper.findByRecipientId(recipientId);
    }

    @Override
    public void markAsRead(Long id, Long recipientId) {
        notificationMapper.markAsRead(id, recipientId);
    }

    @Override
    public int countUnread(Long recipientId) {
        return notificationMapper.countUnread(recipientId);
    }
}
