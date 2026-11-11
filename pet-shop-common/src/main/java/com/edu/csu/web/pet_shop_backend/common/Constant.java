package com.edu.csu.web.pet_shop_backend.common;

import lombok.Data;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.List;

public class Constant {
    @Data
    public static class Result<T> {
        private Integer code;
        private String message;
        private T data;
        private Map<String, Object> metadata;

        public static <T> Result<T> success(T data) {
            Result<T> result = new Result<>();
            result.setCode(200);
            result.setMessage("Success");
            result.setData(data);
            return result;
        }

        public static Result<?> success() {
            return success(null);
        }

        public static <T> Result<T> error(Integer code, String message) {
            Result<T> result = new Result<>();
            result.setCode(code);
            result.setMessage(message);
            return result;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageResult<T> {
        private Long total;
        private List<T> list;
    }
}
