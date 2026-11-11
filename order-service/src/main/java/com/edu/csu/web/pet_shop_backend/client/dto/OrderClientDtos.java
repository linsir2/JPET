package com.edu.csu.web.pet_shop_backend.client.dto;

import java.util.List;
import java.util.Map;

import lombok.Data;

public final class OrderClientDtos {

	private OrderClientDtos() {
	}

	@Data
	public static class CheckoutItem {
		private String lineId;
		private Long spuId;
		private Map<String, String> selection;
		private Integer quantity;
	}

	@Data
	public static class CheckoutRequest {
		private String opId;
		private Long userId;
		private List<CheckoutItem> items;
	}

	@Data
	public static class InventoryReleaseItem {
		private Long spuId;
		private Long skuId;
		private Integer quantity;
	}

	@Data
	public static class InventoryReleaseRequest {
		private String opId;
		private List<InventoryReleaseItem> items;
		private String reason;
	}

	@Data
	public static class MerchantAggregatesRequest {
		private List<Long> spuIds;
		private String currentMonthStart;
		private String nextMonthStart;
		private String previousMonthStart;
	}
}
