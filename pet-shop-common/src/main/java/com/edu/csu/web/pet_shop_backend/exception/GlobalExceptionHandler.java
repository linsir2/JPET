package com.edu.csu.web.pet_shop_backend.exception;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;

import com.edu.csu.web.pet_shop_backend.common.utils.Result;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有继承自 AppException 的自定义异常，返回统一的错误响应格式
     * @param ex 异常对象
     * @return 统一的错误响应结果
     */
    @ExceptionHandler(AppException.class)
    public Result<Map<String, Object>> handleAppException(AppException ex) {
        Map<String, Object> data = new LinkedHashMap<>(ex.getData());
        data.put("errorType", ex.getErrorType());

        Result<Map<String, Object>> result = Result.fail(ex.getCode(), ex.getMessage());
        result.setData(data);
        return result;
    }

    /**
     * 处理参数校验失败的异常，返回包含字段错误信息的统一错误响应
     * @param ex
     * @return
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Map<String, Object>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        Map<String, Object> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        Result<Map<String, Object>> result = Result.fail(400,"参数校验失败");
        result.setData(errors);
        return result;
    }

    /**
     * 处理方法参数级别的校验异常（如 List<@Valid DTO> 等场景）
     * @param ex
     * @return
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Map<String, Object>> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        Map<String, Object> errors = new LinkedHashMap<>();

        for (ParameterValidationResult validationResult : ex.getParameterValidationResults()) {
            String parameterName = validationResult.getMethodParameter() != null
                ? validationResult.getMethodParameter().getParameterName()
                : "parameter";

            if (validationResult instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(fieldError -> {
                    String key = parameterName + "." + fieldError.getField();
                    errors.put(key, fieldError.getDefaultMessage());
                });
                continue;
            }

            for (MessageSourceResolvable resolvable : validationResult.getResolvableErrors()) {
                errors.put(parameterName, resolvable.getDefaultMessage());
            }
        }

        Result<Map<String, Object>> result = Result.fail(400, "参数校验失败");
        result.setData(errors);
        return result;
    }

    /**
     * 处理所有未捕获的异常，返回统一的错误响应
     * @param ex 异常对象
     * @return 统一的错误响应结果
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception ex) {
        return Result.fail(500, "系统异常：" + ex.getMessage());
    }

    //业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException ex) {
        return Result.fail(ex.getCode(), ex.getMessage());
    }
}
