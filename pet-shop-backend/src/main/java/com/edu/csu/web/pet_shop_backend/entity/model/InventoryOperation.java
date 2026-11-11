package com.edu.csu.web.pet_shop_backend.entity.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class InventoryOperation {

	private String opId;

	private String opType;

	private String status;

	private String resultJson;

	private Long userId;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
