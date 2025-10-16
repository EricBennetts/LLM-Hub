package com.example.service;

import com.example.pojo.Comment;
import com.example.pojo.Notification;
import java.util.List;

public interface NotificationService {

    /**
     * 为一条新评论创建通知，并实时推送给帖子作者
     * @param comment 新创建的评论对象
     */
    void createAndPushNotificationForNewComment(Comment comment);

    /**
     * 根据接收者ID查找所有通知
     * @param recipientId 接收者用户ID
     * @return 通知列表
     */
    List<Notification> findByRecipientId(Long recipientId);

    /**
     * 将指定ID的通知标记为已读
     * @param id 通知ID
     * @param recipientId 当前登录的用户ID (用于权限验证)
     */
    void markAsRead(Long id, Long recipientId);

    /**
     * 获取当前用户的未读通知数
     * @param recipientId 当前登录的用户ID
     * @return 未读通知数量
     */
    int countUnread(Long recipientId);
}