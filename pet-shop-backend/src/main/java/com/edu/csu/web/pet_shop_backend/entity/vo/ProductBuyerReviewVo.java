package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductBuyerReviewVo {
	private Long id;
	private String user;
	private String avatar;
	private Integer rating;
	private String text;
	private String date;
}