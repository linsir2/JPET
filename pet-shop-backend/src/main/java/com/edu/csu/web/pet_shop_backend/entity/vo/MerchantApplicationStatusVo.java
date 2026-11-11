package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class MerchantApplicationStatusVo {

	private Long merchantId;

	private String merchantName;

	private String email;

	private String phone;

	private String imageUrl;

	private String status;

	private String auditReason;

	private LocalDateTime createdAt;
}