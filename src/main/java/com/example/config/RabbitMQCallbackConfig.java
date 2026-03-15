package com.example.config;

import com.example.mapper.MessageLogMapper;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class RabbitMQCallbackConfig implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private MessageLogMapper messageLogMapper;

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnsCallback(this);
    }

    /**
     * 消息到达 Exchange 的回调
     * @param correlationData 发送时携带的附加信息（包含消息ID）
     * @param ack 是否成功到达
     * @param cause 失败原因
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (correlationData == null || correlationData.getId() == null) {
            return; // 说明发送消息时没有带ID，忽略不处理
        }

        String messageId = correlationData.getId();
        if (ack) {
            // 成功到达交换机，更新本地消息状态为 1 (成功)
            System.out.println("MQ收到消息确认, messageId: " + messageId);
            messageLogMapper.updateStatus(messageId, 1);
        } else {
            // 到达交换机失败，更新状态为 2 (失败)
            System.err.println("MQ拒收消息, messageId: " + messageId + ", reason: " + cause);
            messageLogMapper.updateStatus(messageId, 2);
        }
    }

    /**
     * 消息从 Exchange 路由到 Queue 失败的回调
     * 如果成功路由了，这个方法不会被调用
     */
    @Override
    public void returnedMessage(ReturnedMessage returned) {
        System.err.println("消息路由失败被退回: " + returned.getMessage());
        // 因为这里无法直接拿到 CorrelationData，通常在路由失败时，
        // 定时任务会因为消息一直处于“0投递中”或被标记为“2投递失败”而自动重试。
    }


}
