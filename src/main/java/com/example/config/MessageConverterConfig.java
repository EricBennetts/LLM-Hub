package com.example.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageConverterConfig {

    /**
     * 为 RabbitMQ 配置一个全局的 JSON 消息转换器。
     * 这将替换掉默认的 Java 原生序列化。
     * @return Jackson2JsonMessageConverter 的实例
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}