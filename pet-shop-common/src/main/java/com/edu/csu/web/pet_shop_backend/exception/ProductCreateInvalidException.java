package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class ProductCreateInvalidException extends AppException {

    public ProductCreateInvalidException(String message, Map<String, Object> data) {
        super(
            400,
            "PRODUCT_CREATE_INVALID",
            message,
            data == null ? new LinkedHashMap<>() : data
        );
    }
}
