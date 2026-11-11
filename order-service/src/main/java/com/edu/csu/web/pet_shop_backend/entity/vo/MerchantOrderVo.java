package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantOrderVo {
	private Long id;
	private String name;
	private String image;
	private BigDecimal amount;
	private BigDecimal unitPrice;
	private Integer quantity;
	private String destination;
	private String receiverName;
	private String receiverPhone;
	private String address;
	private String skuSummary;
	private Map<String, String> skuAttributes;
	private String createdAt;
	private String trackingNo;
	private String currentNode;
	private String progress;
	private BigDecimal mapX;
	private BigDecimal mapY;
	private String updatedAt;
	private String reason;
	private String receiver;
	private Long buyerUserId;
	private String buyerName;
	private String buyerAvatar;
	private String finishedAt;
	private Integer reviewRating;
	private String reviewContent;
	private String reviewerName;
	private String reviewTime;
}