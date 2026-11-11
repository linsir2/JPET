package com.edu.csu.web.pet_shop_backend.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.filter.JWTAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final JWTAuthenticationFilter jwtAuthenticationFilter;
	private final ObjectMapper objectMapper;

	public SecurityConfig(JWTAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.objectMapper = objectMapper;
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(csrf -> csrf.disable())
			.formLogin(form -> form.disable())
			.httpBasic(basic -> basic.disable())
			.headers(headers -> headers.cacheControl(cache -> cache.disable()))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.exceptionHandling(exception -> exception
				.authenticationEntryPoint(authenticationEntryPoint())
				.accessDeniedHandler(accessDeniedHandler())
			)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers("/error", "/api/auth/**").permitAll()
				// InternalTokenFilter enforces X-Internal-Token for /internal/**
				.requestMatchers("/internal/**").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/user/profile").hasAnyAuthority(
					UserRoleEnum.NORMAL_USER.getValue(),
					UserRoleEnum.MERCHANT_USER.getValue()
				)
				.requestMatchers(HttpMethod.DELETE, "/api/user/profile").hasAnyAuthority(
					UserRoleEnum.NORMAL_USER.getValue(),
					UserRoleEnum.MERCHANT_USER.getValue()
				)
				.requestMatchers(HttpMethod.PUT, "/api/user/profile").hasAnyAuthority(
					UserRoleEnum.NORMAL_USER.getValue(),
					UserRoleEnum.MERCHANT_USER.getValue()
				)
				.requestMatchers("/api/admin/users/**").hasAnyAuthority(
					UserRoleEnum.MAIN_ADMIN.getValue(),
					UserRoleEnum.SUB_ADMIN.getValue()
				)
				.anyRequest().permitAll()
			);

		http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public AuthenticationEntryPoint authenticationEntryPoint() {
		return (request, response, authException) -> writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, 401,
			"未登录或登录已失效");
	}

	@Bean
	public AccessDeniedHandler accessDeniedHandler() {
		return (request, response, accessDeniedException) -> writeJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, 403,
			"当前账号无权访问该资源");
	}

	private void writeJsonResponse(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
		response.setStatus(httpStatus);
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
	}
}
