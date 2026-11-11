package com.edu.csu.web.pet_shop_backend.entity.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("merchant_authentication_tb")
public class MerchantAuthentication {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long merchantId;

    private String authenticationName;

    private String companyName;

    private String certificateImageUrl;

    private String status;

    private String auditReason;

    private String authLevel;

    private LocalDate validUntil;

    @TableField(fill= FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill= FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
