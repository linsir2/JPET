package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminRegisterDto {

	@NotBlank(message = "管理员账户不能为空")
	private String account;

	@NotBlank(message = "密码不能为空")
	@Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])[a-zA-Z0-9_]{8,}$",
			message = "密码至少8位密码，只允许包含数字、大小写英文字母和下划线，且必须含有数字和大小写英文字母")
	private String password;

	@NotBlank(message = "注册密钥不能为空")
	private String registerKey;
}