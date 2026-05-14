package com.example.mapper;

import com.example.pojo.ModerationLog;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ModerationLogMapper {

    @Insert("INSERT INTO moderation_log (" +
            "post_id, user_id, decision, post_status, reason, model, latency_ms, " +
            "tool_calls_json, raw_response, error_type, error_message, " +
            "title_snapshot, content_preview, content_length" +
            ") VALUES (" +
            "#{postId}, #{userId}, #{decision}, #{postStatus}, #{reason}, #{model}, #{latencyMs}, " +
            "#{toolCallsJson}, #{rawResponse}, #{errorType}, #{errorMessage}, " +
            "#{titleSnapshot}, #{contentPreview}, #{contentLength}" +
            ")")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ModerationLog moderationLog);

    @Select("SELECT * FROM moderation_log WHERE post_id = #{postId} ORDER BY create_time DESC")
    List<ModerationLog> findByPostId(Long postId);

    @Select("SELECT * FROM moderation_log " +
            "WHERE user_id = #{userId} AND create_time >= #{since} " +
            "ORDER BY create_time DESC")
    List<ModerationLog> findRecentByUserIdSince(@Param("userId") Long userId,
                                                @Param("since") LocalDateTime since);
}
