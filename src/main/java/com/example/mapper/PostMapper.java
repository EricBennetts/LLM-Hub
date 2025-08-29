package com.example.mapper;

import com.example.pojo.Post;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostMapper {

    /**
     * 查询所有帖子，并关联查询出作者的用户名
     * @return 帖子列表
     */
    @Select("SELECT p.*, u.username as authorUsername " +
            "FROM post p " +
            "JOIN user u ON p.user_id = u.id " +
            "ORDER BY p.create_time DESC") // 按创建时间降序排序，最新的帖子在最前面
    List<Post> findAllWithAuthor();

    /**
     * 插入一条帖子
     * @param post 帖子对象
     * @return 插入的行数
     */
    @Insert("INSERT INTO post(title, content, user_id) VALUES(#{title}, #{content}, #{userId})")
    int insertPost(Post post);

    /**
     * 根据id查询帖子，并关联查询出作者的用户名
     * @param id 帖子id
     * @return 帖子对象
     */
    @Select("SELECT p.*, u.username as authorUsername " +
            "FROM post p JOIN user u ON p.user_id = u.id " +
            "WHERE p.id = #{id}")
    Post findById(Long id);
}