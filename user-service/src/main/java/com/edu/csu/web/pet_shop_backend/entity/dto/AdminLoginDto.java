package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminLoginDto {

    @NotBlank(message="管理员账户不能为空")
    private String account;

    @NotBlank(message="密码不能为空")
    private String password;
}
