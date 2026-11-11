package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BuyerOrderAddressDto {
	@NotBlank(message = "收货地址不能为空")
	private String address;
}