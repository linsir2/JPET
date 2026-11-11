package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AppException extends RuntimeException {

    private final int code;
    private final String errorType;
    private final Map<String, Object> data;

    protected AppException(int code, String errorType, String message, Map<String, Object> data) {
        super(message);
        this.code = code;
        this.errorType = errorType;
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }

    public int getCode() {
        return code;
    }

    public String getErrorType() {
        return errorType;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
