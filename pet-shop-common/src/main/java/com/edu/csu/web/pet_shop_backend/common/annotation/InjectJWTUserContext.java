package com.edu.csu.web.pet_shop_backend.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * 自定义注解，用于在 Controller 方法参数中注入 JWTUserContext 对象。
 */
@Target({ElementType.PARAMETER})
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface InjectJWTUserContext {
    
}
