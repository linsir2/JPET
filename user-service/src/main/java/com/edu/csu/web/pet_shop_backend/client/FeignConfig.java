package com.edu.csu.web.pet_shop_backend.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

import feign.RequestInterceptor;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;

@Configuration
public class FeignConfig {

	@Bean
	public InternalTokenFeignInterceptor internalTokenFeignInterceptor(
			@Value("${app.internal.token:}") String token) {
		return new InternalTokenFeignInterceptor(token);
	}

	@Bean
	public Encoder feignEncoder(ObjectMapper objectMapper) {
		return new JacksonEncoder(objectMapper);
	}

	@Bean
	public Decoder feignDecoder(ObjectMapper objectMapper) {
		return new JacksonDecoder(objectMapper);
	}

	@Bean
	public RequestInterceptor jsonContentTypeInterceptor() {
		return template -> template.header("Content-Type", "application/json");
	}
}
