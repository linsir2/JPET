package com.edu.csu.web.pet_shop_backend.client.dto;

import lombok.Data;

/**
 * 调用 user-service PATCH /internal/users/{id} 的请求体。
 */
@Data
public class UserPatchDto {

	private String email;

	private String phone;

	private String role;

	public static UserPatchDto email(String email) {
		UserPatchDto dto = new UserPatchDto();
		dto.setEmail(email);
		return dto;
	}

	public static UserPatchDto phone(String email, String phone) {
		UserPatchDto dto = new UserPatchDto();
		dto.setEmail(email);
		dto.setPhone(phone);
		return dto;
	}

	public static UserPatchDto role(String role) {
		UserPatchDto dto = new UserPatchDto();
		dto.setRole(role);
		return dto;
	}
}
