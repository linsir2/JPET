package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyerProductOptionVo {
	private String label;
	private String value;
	private BigDecimal delta;
}