package com.example.mapper;

import com.example.pojo.User;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户
     * @param username 用户名
     * @return 如果找到用户，则返回 User 对象；否则返回 null
     */
    @Select("select * from user where username = #{username}")
    User findByUsername(String username);

    /**
     * 根据电子邮箱查询用户
     * @param email 电子邮箱
     * @return 如果找到用户，则返回 User 对象；否则返回 null
     */
    @Select("select * from user where email = #{email}")
    User findByEmail(String email);

    /**
     * 插入一个新用户
     * (只插入前端会提供的核心字段，让数据库自动处理默认值)
     */
    @Insert("insert into user (username, password, email) values (#{username}, #{password}, #{email})")
    int insertUser(User user);
}
