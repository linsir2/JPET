package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminRegisterKeyInvalidException extends AppException {

	public AdminRegisterKeyInvalidException(String account) {
		super(403, "ADMIN_REGISTER_KEY_INVALID", "管理员注册密钥错误", buildData(account));
	}

	private static Map<String, Object> buildData(String account) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("account", account);
		return data;
	}
}