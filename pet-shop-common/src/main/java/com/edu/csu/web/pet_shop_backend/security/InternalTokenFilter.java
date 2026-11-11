package com.edu.csu.web.pet_shop_backend.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 内部接口鉴权：只有带正确 X-Internal-Token 的 /internal/** 请求才放行。
 * fail-closed：token 未配置时直接 503，绝不默许。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalTokenFilter extends OncePerRequestFilter {

	private final String expectedToken;
	private final ObjectMapper objectMapper;

	public InternalTokenFilter(@Value("${app.internal.token:}") String expectedToken, ObjectMapper objectMapper) {
		this.expectedToken = expectedToken;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String path = request.getServletPath();
		if (!path.startsWith("/internal/")) {
			filterChain.doFilter(request, response);
			return;
		}

		if (expectedToken == null || expectedToken.isBlank()) {
			write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, 503, "内部接口令牌未配置");
			return;
		}

		String actual = request.getHeader("X-Internal-Token");
		if (!expectedToken.equals(actual)) {
			write(response, HttpServletResponse.SC_UNAUTHORIZED, 401, "内部接口令牌无效");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private void write(HttpServletResponse response, int httpStatus, int code, String message) throws IOException {
		response.setStatus(httpStatus);
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), Result.fail(code, message));
	}
}
