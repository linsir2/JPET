package com.edu.csu.web.pet_shop_backend.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 给所有内部 Feign 调用加 X-Internal-Token。
 * 不加 @Component：只在需要 Feign 的服务里由 FeignConfig 注册为 @Bean。
 */
public class InternalTokenFeignInterceptor implements RequestInterceptor {

	public static final String HEADER = "X-Internal-Token";

	private final String token;

	public InternalTokenFeignInterceptor(String token) {
		this.token = token;
	}

	@Override
	public void apply(RequestTemplate template) {
		if (token != null && !token.isBlank()) {
			template.header(HEADER, token);
		}
	}
}
