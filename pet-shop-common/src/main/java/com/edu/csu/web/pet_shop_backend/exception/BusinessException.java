package com.edu.csu.web.pet_shop_backend.exception;

import lombok.Getter;

/**
 * 业务异常：用于主动抛出带有 HTTP 状态码和消息的业务错误
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    //400 Bad Request
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    //401 Unauthorized
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    //403 Forbidden
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    //404 Not Found
    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    //409 Conflict
    public static BusinessException conflict(String message) {
        return new BusinessException(409, message);
    }
}
