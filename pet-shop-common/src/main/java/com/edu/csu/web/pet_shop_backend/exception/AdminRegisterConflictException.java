package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminRegisterConflictException extends AppException {

	public AdminRegisterConflictException(String account) {
		super(409, "ADMIN_REGISTER_CONFLICT", "管理员账户已存在", buildData(account));
	}

	private static Map<String, Object> buildData(String account) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("account", account);
		return data;
	}
}