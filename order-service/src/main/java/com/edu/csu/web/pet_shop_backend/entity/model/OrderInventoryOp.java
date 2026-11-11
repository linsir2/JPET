package com.edu.csu.web.pet_shop_backend.entity.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class OrderInventoryOp {

	private String opId;

	private Long orderId;

	private String action;

	private String itemsJson;

	private String status;

	private String lastError;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
