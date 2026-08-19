package com.abin.checkrepeatservice.common.utils;

import com.abin.checkrepeatservice.common.Exception.UserAuthException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * 认证信息工具类：用于获取Spring Security认证相关信息
 */
public class AuthInfoUtils {

    /**
     * 私有化构造器：避免工具类被实例化
     */
    private AuthInfoUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    /**
     * 获取当前认证用户信息（UserDetails类型）
     * @return 认证用户详情（未登录时抛出异常）
     */
    public static UserDetails getCurrentUserDetails() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                "anonymousUser".equals(authentication.getPrincipal())) {
            throw new UserAuthException("用户未登录或认证已失效，请重新登录");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            return (UserDetails) principal;
        }

        throw new UserAuthException("未获取到有效的用户认证信息");
    }

    /**
     * 获取当前认证用户名
     * @return 用户名
     */
    public static String getCurrentUsername() {
        UserDetails userDetails = getCurrentUserDetails();
        return userDetails.getUsername();
    }

    /**
     * 获取当前认证用户权限
     * @return 权限集合
     */
    public static Collection<? extends GrantedAuthority> getCurrentUserAuthorities() {
        UserDetails userDetails = getCurrentUserDetails();
        return userDetails.getAuthorities();
    }
}
