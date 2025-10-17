package com.example.utils;

import com.example.pojo.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Map;

public class UserContext {
    public static Long getUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getIdAsLong();
        }
        if ("anonymousUser".equals(principal)) {
            return null;
        }
        System.err.println("警告: UserContext.getUserId() 收到未知的 Principal 类型: " + principal.getClass().getName());
        return null;
    }

    public static String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserPrincipal) {
            return ((UserPrincipal) principal).getUsername();
        }

        return null;
    }
}