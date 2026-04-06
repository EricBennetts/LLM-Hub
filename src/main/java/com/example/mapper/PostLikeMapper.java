package com.example.mapper;

import com.example.pojo.PostLike;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PostLikeMapper {

    @Insert("INSERT INTO post_like(post_id, user_id) VALUES(#{postId}, #{userId})")
    int insert(PostLike postLike);

    @Delete("DELETE FROM post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM post_like WHERE post_id = #{postId} AND user_id = #{userId}")
    int exists(@Param("postId") Long postId, @Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM post_like WHERE post_id = #{postId}")
    int countByPostId(Long postId);

    @Update("UPDATE post SET like_count = like_count + 1 WHERE id = #{postId}")
    int increaseLikeCount(Long postId);

    @Update("UPDATE post SET like_count = CASE WHEN like_count > 0 THEN like_count - 1 ELSE 0 END WHERE id = #{postId}")
    int decreaseLikeCount(Long postId);
}