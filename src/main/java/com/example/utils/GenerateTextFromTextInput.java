package com.example.utils;

import com.example.config.AiConfig;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class GenerateTextFromTextInput {
    
    private static AiConfig aiConfig;
    
    @Autowired
    public void setAiConfig(AiConfig aiConfig) {
        GenerateTextFromTextInput.aiConfig = aiConfig;
    }
    
    public static String AIGen(String prompt) {
        if (aiConfig == null || aiConfig.getApiKey() == null) {
            throw new RuntimeException("AI配置未初始化或API密钥未设置");
        }
        
        Client client = Client.builder().apiKey(aiConfig.getApiKey()).build();
        
        GenerateContentResponse response =
                client.models.generateContent(
                        aiConfig.getModel(),
                        prompt,
                        null);
        
        return response.text();
    }
    
    // 添加实例方法供Spring管理的bean使用
    public String generateText(String prompt) {
        return AIGen(prompt);
    }
}