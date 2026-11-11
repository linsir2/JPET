package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BuyerOrderReviewDto {

	@Min(value = 1, message = "评分最低为1星")
	@Max(value = 5, message = "评分最高为5星")
	private Integer rating;

	@NotBlank(message = "评价内容不能为空")
	@Size(max = 500, message = "评价内容不能超过500字")
	private String content;
}