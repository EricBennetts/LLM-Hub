package com.example.mapper;

import com.example.pojo.Notification;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface NotificationMapper {

    @Insert("INSERT INTO notification (recipient_id, sender_id, post_id, comment_id, type, content, is_read) " +
            "VALUES (#{recipientId}, #{senderId}, #{postId}, #{commentId}, #{type}, #{content}, #{isRead})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Notification notification);

    // 查询某个用户的所有通知，并关联查询出发送者用户名和帖子标题
    @Select("SELECT n.*, u.username as senderUsername, p.title as postTitle " +
            "FROM notification n " +
            "JOIN user u ON n.sender_id = u.id " +
            "JOIN post p ON n.post_id = p.id " +
            "WHERE n.recipient_id = #{recipientId} " +
            "ORDER BY n.create_time DESC")
    List<Notification> findByRecipientId(Long recipientId);

    // 将通知标记为已读 (增加recipientId是为了安全，确保用户只能修改自己的通知)
    @Update("UPDATE notification SET is_read = true WHERE id = #{id} AND recipient_id = #{recipientId}")
    int markAsRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    // 计算某个用户的未读通知数量
    @Select("SELECT COUNT(*) FROM notification WHERE recipient_id = #{recipientId} AND is_read = false")
    int countUnread(Long recipientId);
}