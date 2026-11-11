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
@TableName(value = "`order_tb`", autoResultMap = true)
public class Order {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	private String orderNo;

	private Long userId;

	private Long spuId;

	private String spuName;

	private Long skuId;

	@TableField(value = "sku_attribute_values", typeHandler = JacksonTypeHandler.class)
	private JsonNode skuAttributeValues;

	private Integer quantity;

	private BigDecimal totalPrice;

	private String status;

	private Boolean buyerDeleted;

	private String checkoutOpId;

	private String receiverName;

	private String receiverPhone;

	private String receiverAddress;

	private String paymentMethod;

	private String trackingNo;

	private String shippingProgress;

	private String currentNode;

	private BigDecimal mapX;

	private BigDecimal mapY;

	private String cancelReason;

	private Integer reviewRating;

	private String reviewContent;

	private LocalDateTime reviewTime;

	private LocalDateTime completedTime;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private LocalDateTime updateTime;
}