package com.edu.csu.web.pet_shop_backend.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.edu.csu.web.pet_shop_backend.config.method_argument_resolver.JWTUserContextMethodArgumentResolver;

/**
 * 在所有 MVC 服务中注册 @InjectJWTUserContext 参数解析器。
 * 这样 user-service / product-service / order-service 共用同一份注册逻辑。
 */
@Configuration
public class JwtArgumentResolverConfig implements WebMvcConfigurer {

	private final JWTUserContextMethodArgumentResolver resolver;

	public JwtArgumentResolverConfig(JWTUserContextMethodArgumentResolver resolver) {
		this.resolver = resolver;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(resolver);
	}
}
