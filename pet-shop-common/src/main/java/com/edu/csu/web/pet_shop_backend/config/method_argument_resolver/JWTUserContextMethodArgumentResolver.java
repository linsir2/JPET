package com.edu.csu.web.pet_shop_backend.config.method_argument_resolver;

import com.edu.csu.web.pet_shop_backend.common.JWTContextHolder;
import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class JWTUserContextMethodArgumentResolver implements HandlerMethodArgumentResolver {
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // 判断参数是否有 @InjectJWTUserContext 注解，并且类型是 JWTUserContext
        return parameter.hasParameterAnnotation(InjectJWTUserContext.class) &&
               JWTUserContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        // 从 ThreadLocal 中读取当前请求已解析出的 JWT 用户上下文
        return JWTContextHolder.getContext();
    }
}
