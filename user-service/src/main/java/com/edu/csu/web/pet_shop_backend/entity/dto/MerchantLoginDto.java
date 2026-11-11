package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MerchantLoginDto {

    @NotBlank(message = "用户名或邮箱不能为空")
    private String identifier;

    @NotBlank(message = "密码不能为空")
    private String password;
}
