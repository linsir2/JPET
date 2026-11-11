package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商家入驻申请状态冲突异常。
 */
public class MerchantApplyConflictException extends AppException {

	public MerchantApplyConflictException(Long userId, String merchantStatus, String message) {
		super(409, "MERCHANT_APPLY_CONFLICT", message, buildData(userId, merchantStatus));
	}

	private static Map<String, Object> buildData(Long userId, String merchantStatus) {
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("userId", userId);
		data.put("merchantStatus", merchantStatus);
		return data;
	}
}