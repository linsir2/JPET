package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyerProductVo {
	private Long id;
	private String name;
	private String category;
	private String subCategory;
	private BigDecimal price;
	private Integer heat;
	private Integer purchaseCount;
	private BigDecimal rating;
	private Integer stock;
	private String description;
	private String image;
	private List<String> tags;
	private List<String> coreAttributes;
	private List<BuyerProductOptionVo> sizes;
	private List<BuyerProductOptionVo> specifications;
	private List<ProductSkuVo> skus;
}