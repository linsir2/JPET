package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 账号或商家状态异常导致无法继续操作的异常。
 */
public class AccountStatusInvalidException extends AppException {

	public AccountStatusInvalidException(String subjectType, Long subjectId, String status, String message) {
		super(403, "ACCOUNT_STATUS_INVALID", message, buildData(subjectType, subjectId, status));
	}

	private static Map<String, Object> buildData(String subjectType, Long subjectId, String status) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("subjectType", subjectType);
		data.put("subjectId", subjectId);
		data.put("status", status);
		return data;
	}
}