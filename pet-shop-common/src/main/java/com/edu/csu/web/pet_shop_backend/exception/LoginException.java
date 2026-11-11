package com.edu.csu.web.pet_shop_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

public class LoginException extends AppException {

    public LoginException(String message, Long userId) {
        super(
            401,
            "LOGIN_INVALID",
            message,
            buildData(userId)
        );
    }

    private static Map<String, Object> buildData(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("cause", "JWT_USER_CONTEXT_MISSING");
        return data;
    }
}
