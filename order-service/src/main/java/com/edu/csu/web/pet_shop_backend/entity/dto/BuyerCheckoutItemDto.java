package com.edu.csu.web.pet_shop_backend.entity.dto;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BuyerCheckoutItemDto {

	@NotBlank(message = "商品ID不能为空")
	private String productId;

	@NotBlank(message = "商品名称不能为空")
	private String productName;

	@NotNull(message = "商品数量不能为空")
	@Min(value = 1, message = "商品数量必须大于0")
	private Integer quantity;

	@NotNull(message = "商品单价不能为空")
	@DecimalMin(value = "0.0", inclusive = false, message = "商品单价必须大于0")
	private BigDecimal price;

	private Map<String, String> selection;
}