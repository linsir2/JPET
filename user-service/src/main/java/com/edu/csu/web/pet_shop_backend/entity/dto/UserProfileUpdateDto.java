package com.edu.csu.web.pet_shop_backend.entity.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileUpdateDto {

	@Size(min = 3, max = 50, message = "用户名长度应为 3 到 50 个字符")
	private String username;

	@Email(message = "邮箱格式不正确")
	@Size(max = 100, message = "邮箱长度不能超过 100 个字符")
	private String email;

	@Size(max = 30, message = "手机号长度不能超过 30 个字符")
	private String phone;

	@Size(max = 500, message = "个人简介不能超过 500 个字符")
	private String intro;

	@Size(max = 255, message = "收货地址不能超过 255 个字符")
	private String address;

	@Size(max = 30, message = "偏好标签数量不能超过 30 个")
	private List<@Size(max = 50, message = "单个偏好标签长度不能超过 50 个字符") String> preferences;
}