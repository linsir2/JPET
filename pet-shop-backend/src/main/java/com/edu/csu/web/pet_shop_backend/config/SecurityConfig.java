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

    /**
     * 安全过滤链配置。
     * 商户接口要求商家 token，管理员接口要求管理员 token，其余接口暂时放行。
     * @param http
     * @return
     * @throws Exception
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 Spring Security 默认的 CSRF、表单登录、HTTP Basic 认证等功能，改用基于 JWT 的认证方式   
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .headers(headers -> headers.cacheControl(cache -> cache.disable()))

            // 设置 Session 策略为 STATELESS，完全依赖 JWT 进行认证，不使用服务器端 Session
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 配置异常处理，「未认证」和「无权限访问」时返回 JSON 格式的错误响应
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint()) // 未认证（未登录或登录失效）
                .accessDeniedHandler(accessDeniedHandler()) // 无权限访问（权限不足）
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // 错误路径和登录注册接口放行
                .requestMatchers("/error", "/api/auth/**").permitAll()

                // 普通用户登录后允许提交商家入驻申请，真正的商家管理能力仍要求 merchant_user 角色
                .requestMatchers(HttpMethod.POST, "/api/merchant/apply").hasAnyAuthority(
                    UserRoleEnum.NORMAL_USER.getValue(),
                    UserRoleEnum.MERCHANT_USER.getValue()
                )
                .requestMatchers(HttpMethod.POST, "/api/merchant/apply/image").hasAnyAuthority(
                    UserRoleEnum.NORMAL_USER.getValue(),
                    UserRoleEnum.MERCHANT_USER.getValue()
                )
                .requestMatchers(HttpMethod.POST, "/api/merchant/apply/status").hasAnyAuthority(
                    UserRoleEnum.NORMAL_USER.getValue(),
                    UserRoleEnum.MERCHANT_USER.getValue()
                )
                .requestMatchers(HttpMethod.DELETE, "/api/merchant/apply").hasAnyAuthority(
                    UserRoleEnum.NORMAL_USER.getValue(),
                    UserRoleEnum.MERCHANT_USER.getValue()
                )

                .requestMatchers(HttpMethod.POST, "/api/merchant/authentications/**").hasAuthority(
                    UserRoleEnum.MERCHANT_USER.getValue()
                )
                .requestMatchers(HttpMethod.GET, "/api/merchant/authentications").hasAuthority(
                    UserRoleEnum.MERCHANT_USER.getValue()
                )

                // 商户接口需要 MERCHANT_USER 角色，管理员接口需要 MAIN_ADMIN 或 SUB_ADMIN 角色
                .requestMatchers("/api/merchant/**").hasAuthority(UserRoleEnum.MERCHANT_USER.getValue())
                .requestMatchers("/api/admin/**").hasAnyAuthority(
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
