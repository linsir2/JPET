package com.edu.csu.web.pet_shop_backend.entity.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class CheckoutOp {

	private String opId;

	private Long userId;

	private String status;

	private String itemsJson;

	private String reserveResultJson;

	private String lastError;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
