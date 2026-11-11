package com.edu.csu.web.pet_shop_backend.common;

import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;

/**
 * 线程内 JWT 上下文；{@link #getContext()} 与 {@link #setContext(JWTUserContext)} 均使用
 * {@link com.edu.csu.web.pet_shop_backend.entity.model.JWTUserContext}（勿用 {@code entity.pojo} 类型变量接收返回值）。
 */
public class JWTContextHolder {
    private static final ThreadLocal<JWTUserContext> CONTEXT_HOLDER = new ThreadLocal<>();

    // ==== 提供给 Filter 使用的方法 ====
    public static void setContext(JWTUserContext context) {
        CONTEXT_HOLDER.set(context);
    }

    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    
    // ==== 提供给业务代码使用的方法 ====
    public static JWTUserContext getContext() {
        return CONTEXT_HOLDER.get();
    }

    public static Long getUserId() {
        JWTUserContext context = getContext();
        return context != null ? context.getUserId() : null;
    }

    public static String getUsername() {
        JWTUserContext context = getContext();
        return context != null ? context.getUsername() : null;
    }

    public static UserRoleEnum getRole() {
        JWTUserContext context = getContext();
        return context != null ? context.getRole() : null;
    }
}
