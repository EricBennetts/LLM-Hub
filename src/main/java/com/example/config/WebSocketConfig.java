// ===== 文件: config/WebSocketConfig.java =====
package com.example.config;

import com.example.pojo.UserPrincipal;
import com.example.utils.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker // 启用 STOMP 协议的 WebSocket 消息代理
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // 允许所有来源
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/user")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartBeatScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Bean
    public TaskScheduler heartBeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    // ================== 核心修复：增加 WebSocket 连接时的 JWT 拦截与用户绑定 ==================
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                // 只在客户端发起 CONNECT 请求时拦截并鉴权
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // 提取前端传入的 STOMP 头部 Authorization
                    List<String> authorization = accessor.getNativeHeader("Authorization");
                    if (authorization != null && !authorization.isEmpty()) {
                        String token = authorization.get(0);
                        if (token.startsWith("Bearer ")) {
                            token = token.substring(7);
                            try {
                                // 解析 Token，复用你的 JwtUtil
                                Map<String, Object> claims = JwtUtil.parseToken(token);

                                // 创建 UserPrincipal（注意：你的 UserPrincipal 已经重写了 getName() 返回 userId，非常完美）
                                UserPrincipal userPrincipal = new UserPrincipal(claims);

                                // 【最关键的一步】将解析出的用户信息绑定到这个 STOMP Session 上
                                accessor.setUser(userPrincipal);
                                System.out.println("[WebSocket] 客户端鉴权成功，绑定用户ID: " + userPrincipal.getName());
                            } catch (Exception e) {
                                System.err.println("[WebSocket] Token 验证失败，连接可能无法接收特定用户消息: " + e.getMessage());
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}