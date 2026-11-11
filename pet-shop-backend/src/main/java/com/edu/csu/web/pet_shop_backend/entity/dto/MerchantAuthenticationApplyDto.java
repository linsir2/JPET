package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MerchantAuthenticationApplyDto {

    @NotBlank(message = "公司名称不能为空")
    @Size(max = 100, message = "公司名称不能超过100个字符")
    private String companyName;

    @NotBlank(message = "证书图片不能为空")
    @Size(max = 500, message = "证书图片地址不能超过500个字符")
    private String certificateImageUrl;
}