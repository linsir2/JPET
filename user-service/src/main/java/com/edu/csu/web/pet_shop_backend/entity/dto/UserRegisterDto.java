package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class UserRegisterDto {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message="邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp="^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])[a-zA-Z0-9_]{8,}$",
            message="密码至少8位密码，只允许包含数字、大小写英文字母和下划线，且必须含有数字和大小写英文字母"
    )
    private String password;
}
