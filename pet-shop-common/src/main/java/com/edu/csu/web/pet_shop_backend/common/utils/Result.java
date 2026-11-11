package com.edu.csu.web.pet_shop_backend.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一API响应结果封装类
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result<T> {

	private int code;
	private String message;
	private T data;
	private Map<String, Object> metadata;

	public Result() {
		this.metadata = new LinkedHashMap<>();
	}

	public Result(int code, String message, T data, Map<String, Object> metadata) {
		this.code = code;
		this.message = message;
		this.data = data;
		this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
	}

	public static <T> Result<T> success(T data) {
		return new Result<>(200, "Success", data, new LinkedHashMap<>());
	}

	public static <T> Result<T> success(T data, Map<String, Object> metadata) {
		return new Result<>(200, "Success", data, metadata);
	}

	public static <T> Result<T> success(String message, T data) {
		return new Result<>(200, message, data, new LinkedHashMap<>());
	}

	public static <T> Result<T> success(String message, T data, Map<String, Object> metadata) {
		return new Result<>(200, message, data, metadata);
	}

	public static <T> Result<T> fail(int code, String message) {
		return new Result<>(code, message, null, new LinkedHashMap<>());
	}

	public static <T> Result<T> fail(int code, String message, Map<String, Object> metadata) {
		return new Result<>(code, message, null, metadata);
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public T getData() {
		return data;
	}

	public void setData(T data) {
		this.data = data;
	}

	public Map<String, Object> getMetadata() {
		return metadata;
	}

	public void setMetadata(Map<String, Object> metadata) {
		this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
	}
}
