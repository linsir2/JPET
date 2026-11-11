package com.edu.csu.web.pet_shop_backend.entity.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "`merchant_tb`", autoResultMap = true)
public class Merchant {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	private Long userId;

	private String merchantName;

	private LocalDateTime lastMerchantNameChangeTime;

	private String imageUrl;

	private BigDecimal turnover;

	private String description;

	private String status;

	private String auditReason;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;
}
