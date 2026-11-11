package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSkuVo {
    
	private Long id;

	private Map<String, Object> attributeValues;

	private String status;

	private String imageUrl;

	private BigDecimal price;

	private Integer stock;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
