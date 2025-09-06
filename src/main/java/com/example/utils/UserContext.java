package com.example.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;

public class UserContext {
    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
        Number userIdNumber = (Number) claims.get("id");
        return userIdNumber.longValue();
    }
}