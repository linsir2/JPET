package com.edu.csu.web.pet_shop_backend.entity.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName(value = "`product_sku_tb`", autoResultMap = true)
public class ProductSKU {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	private Long spuId;

	@TableField(value = "attribute_values", typeHandler = JacksonTypeHandler.class)
	private JsonNode attributeValues;

	private String status;

	private Integer isActive;

	private String imageUrl;

	private BigDecimal price;

	private Integer stock;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;
}
