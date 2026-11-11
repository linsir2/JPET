package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class MerchantStatsVo {

	private BigDecimal monthRevenue;

	private BigDecimal totalRevenue;

	private BigDecimal trendPercent;

	private BigDecimal rating;
}