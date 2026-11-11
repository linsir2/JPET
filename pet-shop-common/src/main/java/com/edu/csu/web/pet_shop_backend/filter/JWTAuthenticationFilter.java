package com.edu.csu.web.pet_shop_backend.filter;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.edu.csu.web.pet_shop_backend.common.JWTContextHolder;
import com.edu.csu.web.pet_shop_backend.common.utils.JwtUtil;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 基于 JWT 的认证过滤器。
 * 负责解析 access_token，并将当前请求的用户信息写入 ThreadLocal 与 SecurityContext。
 */
@Component
public class JWTAuthenticationFilter extends OncePerRequestFilter {

	private static final String BEARER_PREFIX = "Bearer ";

	private final JwtUtil jwtUtil;
	private final ObjectMapper objectMapper;

	public JWTAuthenticationFilter(JwtUtil jwtUtil, ObjectMapper objectMapper) {
		this.jwtUtil = jwtUtil;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		try {
			String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
			boolean protectedPath = isProtectedPath(request);

            // --- 参数校验 ---
			if (authorizationHeader == null || authorizationHeader.isBlank()) {
				if (protectedPath) {
					writeUnauthorized(response, "未提供 access_token");
					return;
				}
				filterChain.doFilter(request, response);
				return;
			}

			if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
				writeUnauthorized(response, "Authorization 请求头格式错误");
				return;
			}

			String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
			if (token.isEmpty()) {
				writeUnauthorized(response, "access_token 不能为空");
				return;
			}

			Claims claims = jwtUtil.parseToken(token);
			if (!jwtUtil.isAccessToken(claims)) {
				writeUnauthorized(response, "当前凭证不是 access_token");
				return;
			}

            // --- 构建用户上下文 ---
			UserRoleEnum role = jwtUtil.getRole(claims);
			JWTUserContext jwtUserContext = new JWTUserContext(
				jwtUtil.getUserId(claims),
				role,
				jwtUtil.getUsername(claims)
			);

			JWTContextHolder.setContext(jwtUserContext);

            // --- 构建 Authentication 对象并存入 SecurityContext ---
			UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
				jwtUserContext,
				token,
				List.of(new SimpleGrantedAuthority(role.getValue()))
			);
			authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
			SecurityContextHolder.getContext().setAuthentication(authenticationToken);

			filterChain.doFilter(request, response);
		} catch (JwtException | IllegalArgumentException ex) {
			writeUnauthorized(response, "无效或已过期的登录凭证");
		} finally {
			JWTContextHolder.clearContext();
			SecurityContextHolder.clearContext();
		}
	}

    /**
     * 判断当前请求是否需要进行 JWT 认证保护（即是否访问了受保护的 API 路径）
     * @param request
     * @return
     */
	private boolean isProtectedPath(HttpServletRequest request) {
		String servletPath = request.getServletPath();
		return servletPath.startsWith("/api/merchant/") || servletPath.startsWith("/api/admin/");
	}

	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setCharacterEncoding("UTF-8");
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getWriter(), Result.fail(401, message));
	}
}