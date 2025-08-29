package com.example.config;

import com.example.interceptor.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter; // 注入过滤器

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // --- 关键改动 1: 关闭默认的 session 管理 ---
                // 因为我们用JWT，是无状态的，不需要session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // authorizeHttpRequests 为 AuthorizationFilter 编写一本“规则手册”
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/users/register", "/users/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
                        .anyRequest().authenticated()
                );

        // --- 关键改动 2: 添加你的 JWT 过滤器 ---
        // 告诉 Spring Security，在进行用户名密码认证前，先执行我们的 JWT 过滤器
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允许来自你的前端的跨域请求 (http://localhost:63342)
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:63342"));
        // 允许所有请求方法 (GET, POST, PUT, DELETE, etc.)
        configuration.setAllowedMethods(Arrays.asList("*"));
        // 允许所有请求头
        configuration.setAllowedHeaders(Arrays.asList("*"));
        // 允许浏览器发送凭证 (如 cookies)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有 URL 应用这个配置
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}