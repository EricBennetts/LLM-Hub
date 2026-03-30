package com.example.job;

import com.example.mapper.MessageLogMapper;
import com.example.pojo.MessageLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class MessageRetryJob {

    @Autowired
    private MessageLogMapper messageLogMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // 每 30 秒执行一次
    @Scheduled(cron = "0/30 * * * * ?")
    public void retrySend() {
        // 1. 捞取所有状态为 0 (发送中) 或 2 (失败)，且下次重试时间 <= 当前时间，且重试次数 < 3 的记录
        List<MessageLog> logs = messageLogMapper.selectTimeoutMessages();

        if (logs == null || logs.isEmpty()) {
            return;
        }

        System.out.println("--- 触发消息补偿重试任务，待处理条数: " + logs.size() + " ---");

        for (MessageLog log : logs) {
            String messageId = log.getMessageId();
            
            // 2. 更新重试次数和下次重试时间 (指数退避：1分钟, 5分钟, 15分钟)
            int currentTry = log.getTryCount() + 1;
            LocalDateTime nextTime;
            if (currentTry == 1) nextTime = LocalDateTime.now().plusMinutes(5);
            else if (currentTry == 2) nextTime = LocalDateTime.now().plusMinutes(15);
            else {
                // 达到 3 次，不再自动重试，保持失败状态
                messageLogMapper.updateRetryInfo(messageId, 2, null);
                continue; // 跳过发送
            }
            messageLogMapper.updateRetryInfo(messageId, 0, nextTime);

            // 3. 重新发送消息
            try {
                System.out.println("正在重试发送消息: " + messageId);
                CorrelationData correlationData = new CorrelationData(messageId);
                
                // 因为当时存入 content 的是 JSON 字符串，我们需要反序列化回原本的格式
                // 你的 email 监听器接收的是 String 参数
                String email = objectMapper.readValue(log.getContent(), String.class);

                rabbitTemplate.convertAndSend(log.getExchange(), log.getRoutingKey(), email, correlationData);
            } catch (Exception e) {
                System.err.println("重试发送消息异常: " + messageId + ", 错误: " + e.getMessage());
            }
        }
    }
}