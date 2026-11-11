package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class AuthenticationVo {
    private Long id;
    private Long merchantId;
    private String authenticationName;
    private String companyName;
    private String certificateImageUrl;
    private String status;
    private String auditReason;
    private String authLevel;
    private LocalDate validUntil;
    private LocalDateTime createdAt;
}