package com.edu.csu.web.pet_shop_backend.entity.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "`product_spu_tb`", autoResultMap = true)
public class ProductSPU {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	private Long merchantId;

	private String name;

	private String description;

	private String imageUrl;

	private String status;

	private Integer isActive;

	private String auditReason;

	private BigDecimal minPrice;

	/** 商品总销量（只读，由系统维护，商家不可写入） */
	private Integer sales;

	@TableField(value = "core_attributes", typeHandler = JacksonTypeHandler.class)
	private JsonNode coreAttributes;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;
}
