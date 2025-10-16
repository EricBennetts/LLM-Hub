package com.example.controller;

import com.example.pojo.Notification;
import com.example.pojo.Result;
import com.example.service.NotificationService;
import com.example.utils.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/notifications") // 使用/api前缀，便于未来统一管理
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    /**
     * 获取当前登录用户的所有通知
     */
    @GetMapping
    public Result<List<Notification>> getMyNotifications() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }
        List<Notification> notifications = notificationService.findByRecipientId(currentUserId);
        return Result.success(notifications);
    }

    /**
     * 将单个通知标记为已读
     */
    @PostMapping("/{id}/read")
    public Result markAsRead(@PathVariable Long id) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }
        notificationService.markAsRead(id, currentUserId);
        return Result.success();
    }
    
    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            return Result.error("用户未登录");
        }
        int count = notificationService.countUnread(currentUserId);
        return Result.success(count);
    }
}