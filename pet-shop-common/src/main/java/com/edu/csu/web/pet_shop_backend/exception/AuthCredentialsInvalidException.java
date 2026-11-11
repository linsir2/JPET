package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 登录或 token 刷新时凭证无效的异常。
 */
public class AuthCredentialsInvalidException extends AppException {

	public AuthCredentialsInvalidException(String loginType, String principal, String message) {
		super(401, "AUTH_CREDENTIALS_INVALID", message, buildData(loginType, principal));
	}

	private static Map<String, Object> buildData(String loginType, String principal) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("loginType", loginType);
		data.put("principal", principal);
		return data;
	}
}