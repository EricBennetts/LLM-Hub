package com.example;

import com.example.utils.GenerateTextFromTextInput;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class PromptTest {

    @Autowired
    private GenerateTextFromTextInput generateTextFromTextInput;

    @Test
    void testAiGeneration() {
        System.out.println("开始测试AI生成功能");
        try {
            // 调用实例方法而不是静态方法
            String result = generateTextFromTextInput.generateText("Explain AI in simple words");
            System.out.println("AI生成结果:");
            System.out.println(result);
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}