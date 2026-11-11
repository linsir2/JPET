package com.edu.csu.web.pet_shop_backend.entity.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class BuyerCheckoutDto {

	@NotBlank(message = "收件人不能为空")
	private String receiver;

	@NotBlank(message = "手机号不能为空")
	private String phone;

	@NotBlank(message = "地址不能为空")
	private String address;

	@NotBlank(message = "支付方式不能为空")
	private String paymentMethod;

	@NotEmpty(message = "订单商品不能为空")
	private List<@Valid BuyerCheckoutItemDto> items;
}