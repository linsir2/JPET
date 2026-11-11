package com.edu.csu.web.pet_shop_backend.exception;

import java.util.Map;

public class MerchantNotFoundException extends AppException {

    public MerchantNotFoundException(Long userId) {
        super(
            403,
            "MERCHANT_NOT_FOUND",
            "当前用户尚未完成商家认证或商家信息不存在",
            Map.of("userId", userId)
        );
    }
}
