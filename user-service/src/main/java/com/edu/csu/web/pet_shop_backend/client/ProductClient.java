package com.edu.csu.web.pet_shop_backend.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.edu.csu.web.pet_shop_backend.client.dto.MerchantApplicationStatusBrief;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;

@FeignClient(name = "product-service", path = "/internal/products")
public interface ProductClient {

	@GetMapping("/merchants/by-user/{userId}/application-status")
	Result<MerchantApplicationStatusBrief> applicationStatus(@PathVariable("userId") Long userId);
}
