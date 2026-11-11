package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyerOrderVo {
	private Long id;
	private String orderNo;
	private Long productId;
	private String name;
	private String image;
	private BigDecimal amount;
	private BigDecimal unitPrice;
	private Integer quantity;
	private String status;
	private String progress;
	private String currentNode;
	private String trackingNo;
	private Long merchantId;
	private String merchantName;
	private String paymentMethod;
	private String receiverName;
	private String receiverPhone;
	private String address;
	private String skuSummary;
	private Map<String, String> skuAttributes;
	private String createdAt;
	private String completedAt;
	private boolean canReview;
	private BuyerOrderReviewVo review;
}