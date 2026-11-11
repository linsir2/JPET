package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户注册时用户名或邮箱冲突的异常。
 */
public class UserRegisterConflictException extends AppException {

	public UserRegisterConflictException(String field, String value) {
		super(409, "USER_REGISTER_CONFLICT", buildMessage(field), buildData(field, value));
	}

	private static String buildMessage(String field) {
		return switch (field) {
			case "username" -> "用户名已存在";
			case "email" -> "邮箱已存在";
			default -> "注册信息已存在";
		};
	}

	private static Map<String, Object> buildData(String field, String value) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("field", field);
		data.put("value", value);
		return data;
	}
}