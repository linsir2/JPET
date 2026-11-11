package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 通用配置类。
 * 注意：本项目同时存在 Boot 4 的 Jackson 3（MVC）和显式 Jackson 2（JsonNode/JacksonTypeHandler/Feign）。
 * 这里把 Jackson 2 也设为 SNAKE_CASE，保证 MVC 与 Feign 的内部调用字段命名一致。
 */
@Configuration
public class AppConfig {

	@Bean
	public ObjectMapper objectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
		mapper.registerModule(new JavaTimeModule());
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		return mapper;
	}
}
