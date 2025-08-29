package com.example.pojo;


import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    private Long id;

    private String username;


    private String password;

    private String email;

    // MyBatis 可以通过配置自动映射到数据库的下划线命名法 (avatar_url)
    private String avatarUrl;

    private String bio;

    private String role;


    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}