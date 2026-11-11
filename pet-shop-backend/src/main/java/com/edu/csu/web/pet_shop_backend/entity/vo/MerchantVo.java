package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MerchantVo {
    private Long id;
    private Long userId;
    private String username;
    private String email;
    private String phone;
    private String merchantName;
    private String imageUrl;
    private String description;
    private String status;
    private String auditReason;
    private LocalDateTime createdAt;
}
