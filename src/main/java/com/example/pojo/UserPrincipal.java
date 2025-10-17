package com.example.pojo;

import java.security.Principal;
import java.util.Map;

/**
 * 自定义 Principal 对象，用于封装JWT中的用户信息。
 * 这个类作为 Spring Security 上下文中的“当事人”，
 * 既能满足WebSocket通过 getName() 获取唯一标识的需求，
 * 又能为业务代码提供完整的用户信息。
 */
public class UserPrincipal implements Principal {

    private final String id; // 用户ID，字符串形式
    private final String username; // 用户名
    private final Map<String, Object> claims; // 存储所有原始信息

    public UserPrincipal(Map<String, Object> claims) {
        // 从 claims 中安全地提取 id 和 username
        this.id = claims.get("id").toString();
        this.username = (String) claims.get("username");
        this.claims = claims;
    }

    /**
     * 【核心】重写 getName() 方法，使其返回用户的唯一ID字符串。
     * 这是 Spring WebSocket 用来识别用户、进行点对点消息推送的关键。
     * @return 用户的ID字符串
     */
    @Override
    public String getName() {
        return this.id;
    }

    /**
     * 获取完整的 claims Map
     * @return 包含所有JWT声明的Map
     */
    public Map<String, Object> getClaims() {
        return this.claims;
    }

    /**
     * 便捷方法，以 Long 类型返回用户ID
     * @return 用户的ID
     */
    public Long getIdAsLong() {
        return Long.parseLong(this.id);
    }

    /**
     * 便捷方法，返回用户名
     * @return 用户名
     */
    public String getUsername() {
        return this.username;
    }
}