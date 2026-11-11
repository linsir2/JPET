package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuyerOrderReviewVo {
	private Integer rating;
	private String content;
	private String reviewerName;
	private String createdAt;
}