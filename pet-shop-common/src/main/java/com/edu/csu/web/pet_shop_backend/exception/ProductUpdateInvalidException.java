package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProductUpdateInvalidException extends AppException {

    public ProductUpdateInvalidException(String message, Map<String, Object> data) {
        super(
            400,
            "PRODUCT_UPDATE_INVALID",
            message,
            data == null ? new LinkedHashMap<>() : data
        );
    }
}
