package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // 启用 STOMP 协议的 WebSocket 消息代理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册一个 STOMP 端点，客户端将使用它来连接 WebSocket 服务器
        // "/ws" 是连接的端点路径
        // withSockJS() 是为不支持 WebSocket 的浏览器提供备用选项
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:63342").withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 配置消息代理
        // 启用一个简单的内存消息代理，并将消息的目标前缀设置为 "/topic" 和 "/user"
        // "/user" 前缀用于点对点消息
        registry.enableSimpleBroker("/topic", "/user");
        // 设置客户端发送消息的目标前缀
        registry.setApplicationDestinationPrefixes("/app");
    }
}