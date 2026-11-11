package com.edu.csu.web.pet_shop_backend.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * product-service 内部接口契约（供 order-service 通过 Feign 调用）。
 * 字段名与 order-service 侧同名 DTO 保持一致。
 */
public final class InternalProductContracts {

	private InternalProductContracts() {
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
	public static class InventoryOpView {
		private String opId;
		private String opType;
		private String status;
		private List<CheckoutItemSnapshot> snapshots;
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
	public static class MerchantApplicationStatusBrief {
		private Long merchantId;
		private String status;
	}
}
