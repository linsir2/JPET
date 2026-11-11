package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MerchantApplyDto {

    @NotBlank(message = "商户名称不能为空")
    private String merchantName;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100字")
    private String email;

    @NotBlank(message = "联系方式不能为空")
    @Size(max = 30, message = "联系方式长度不能超过30字")
    private String phone;

    @NotBlank(message="商家图片不能为空")
    private String imageUrl;

    @Size(max=500,message="商户简介不能超过500字")
    private String description;

    private List<String> tags;
}
