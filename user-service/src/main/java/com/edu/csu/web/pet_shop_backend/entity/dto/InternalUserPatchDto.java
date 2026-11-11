package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.Data;

/**
 * 内部局部更新：只改传入的字段，profile 的 phone 按字段 merge。
 */
@Data
public class InternalUserPatchDto {

	private String email;

	private String phone;

	private String role;
}
