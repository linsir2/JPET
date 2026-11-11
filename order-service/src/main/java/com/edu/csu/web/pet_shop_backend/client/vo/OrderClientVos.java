package com.edu.csu.web.pet_shop_backend.client.vo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public final class OrderClientVos {

	private OrderClientVos() {
	}

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class CheckoutItemSnapshot {
		private String lineId;
		private Long spuId;
		private String spuName;
		private Long skuId;
		private Integer quantity;
		private BigDecimal unitPrice;
		private Map<String, String> skuAttributes;
	}

	@Data
	public static class SpuBrief {
		private Long id;
		private String name;
		private String imageUrl;
		private Long merchantId;
		private String merchantName;
		private Long merchantUserId;
		private Boolean isActive;
		private String status;
	}

	@Data
	public static class CatalogSpu {
		private Long id;
		private String name;
		private String imageUrl;
		private Boolean isActive;
		private String status;
	}

	@Data
	public static class MerchantCatalog {
		private Long merchantId;
		private List<CatalogSpu> spus;
	}

	@Data
	public static class InventoryOpView {
		private String opId;
		private String opType;
		private String status;
		private List<CheckoutItemSnapshot> snapshots;
	}
}
