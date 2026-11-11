package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
public class MerchantAuditDto {
    @NotBlank(message = "审核状态不能为空")
    @Pattern(regexp = "active|rejected", message = "审核状态只能是 active 或 rejected")
    private String auditStatus;

    private String auditReason;
}
