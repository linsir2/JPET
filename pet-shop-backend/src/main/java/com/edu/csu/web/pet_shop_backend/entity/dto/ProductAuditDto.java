package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class ProductAuditDto {
    @NotBlank(message = "审核状态不能为空")
    private String auditStatus; 
    private String auditReason; 
}