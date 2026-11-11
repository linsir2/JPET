package com.edu.csu.web.pet_shop_backend.exception;

import java.util.Map;

public class ProductNotFoundException extends AppException {

    public ProductNotFoundException(Long productId, Long merchantId) {
        super(
            404,
            "PRODUCT_NOT_FOUND",
            "商品不存在或无权限访问",
            Map.of("productId", productId, "merchantId", merchantId)
        );
    }
}
