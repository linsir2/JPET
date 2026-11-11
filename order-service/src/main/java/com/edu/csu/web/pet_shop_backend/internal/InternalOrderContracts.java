package com.edu.csu.web.pet_shop_backend.internal;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class InternalOrderContracts {

	private InternalOrderContracts() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ReviewItem {
		private Long id;
		private Long userId;
		private Integer rating;
		private String content;
		private String reviewTime;
	}

	@Data
	public static class MerchantAggregatesRequest {
		private List<Long> spuIds;
		private String currentMonthStart;
		private String nextMonthStart;
		private String previousMonthStart;
	}

	@Data
	public static class MerchantAggregateResponse {
		private BigDecimal currentRevenue = BigDecimal.ZERO;
		private BigDecimal previousRevenue = BigDecimal.ZERO;
		private BigDecimal totalRevenue = BigDecimal.ZERO;
		private BigDecimal averageRating = BigDecimal.ZERO;
	}
}
