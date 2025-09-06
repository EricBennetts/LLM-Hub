package com.example.mapper;

import com.example.pojo.Comment;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommentMapper {

    @Insert("INSERT INTO comment (content, user_id, post_id) VALUES (#{content}, #{userId}, #{postId})")
    int insert(Comment comment);

    @Select("SELECT c.*, u.username as authorUsername FROM comment c " +
            "JOIN user u ON c.user_id = u.id " +
            "WHERE c.post_id = #{postId} " +
            "ORDER BY c.create_time ASC")
    List<Comment> findByPostId(Long postId);

    @Update("UPDATE comment SET content = #{content} WHERE id = #{id} AND user_id = #{userId}")
    int update(Comment comment);

    @Delete("DELETE FROM comment WHERE id = #{id} AND user_id = #{userId}")
    int delete(Long id, Long userId);

    @Select("SELECT * FROM comment WHERE id = #{id}")
    Comment findById(Long id);
}