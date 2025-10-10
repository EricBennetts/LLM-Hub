package com.example;

import com.example.mapper.PostMapper;
import com.example.pojo.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Random;

@SpringBootTest
public class DataInsertionTest {

    @Autowired
    private PostMapper postMapper;

    @Test
    void insertBulkPosts() {
        System.out.println("--- 开始批量插入帖子数据 ---");
        for (int i = 1; i <= 10000; i++) {
            Post post = new Post();
            post.setTitle("测试帖子标题 " + i);
            post.setContent("这是自动生成的帖子内容，序号为：" + i + "。后面可以跟一些随机的长文本...");

            long userId = 1L;
            post.setUserId(userId);
            
            postMapper.insertPost(post);

            if (i % 100 == 0) {
                System.out.println("已插入 " + i + " 条数据...");
            }
        }
        System.out.println("--- 批量插入数据完成 ---");
    }
}