package com.example.interceptor;

import com.example.pojo.UserPrincipal;
import com.example.utils.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("Authorization");

        // 如果没有token，或者请求的是公共路径，直接放行
        if (token == null || !token.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String actualToken = token.substring(7);
            Map<String, Object> claims = JwtUtil.parseToken(actualToken);

            // 解析成功，将认证信息存入 Spring Security 的上下文
            // 这样，Spring Security 就知道当前用户是谁，并且是已认证的
            UserPrincipal userPrincipal = new UserPrincipal(claims);
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userPrincipal, null, null);
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

        } catch (Exception e) {
            // 安全上下文被清空，之后的AuthorizationFilter会发现需要认证但找不到认证信息，就判定为认证失败
            SecurityContextHolder.clearContext();
        }

        // 无论验证成功与否，都继续执行过滤器链
        filterChain.doFilter(request, response);
    }
}