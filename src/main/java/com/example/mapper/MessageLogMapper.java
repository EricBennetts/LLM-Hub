package com.example.mapper;

import com.example.pojo.MessageLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MessageLogMapper {

    @Insert("INSERT INTO message_log (message_id, exchange, routing_key, content, status, try_count, next_retry_time) " +
            "VALUES (#{messageId}, #{exchange}, #{routingKey}, #{content}, #{status}, #{tryCount}, #{nextRetryTime})")
    int insert(MessageLog messageLog);

    @Update("UPDATE message_log SET status = #{status}, update_time = NOW() WHERE message_id = #{messageId}")
    int updateStatus(@Param("messageId") String messageId, @Param("status") Integer status);

    // 更新重试次数和下次重试时间
    @Update("UPDATE message_log SET try_count = try_count + 1, status = #{status}, next_retry_time = #{nextRetryTime}, update_time = NOW() WHERE message_id = #{messageId}")
    int updateRetryInfo(@Param("messageId") String messageId, @Param("status") Integer status, @Param("nextRetryTime") LocalDateTime nextRetryTime);

    // 扫描需要重试的消息 (状态为 0 或 2，且下次重试时间早于当前时间，且重试次数小于 3)
    @Select("SELECT * FROM message_log WHERE status IN (0, 2) AND next_retry_time <= NOW() AND try_count < 3")
    List<MessageLog> selectTimeoutMessages();
}