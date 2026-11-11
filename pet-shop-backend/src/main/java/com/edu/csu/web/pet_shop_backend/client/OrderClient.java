package com.edu.csu.web.pet_shop_backend.client;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.MerchantAggregateResponse;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.MerchantAggregatesRequest;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.ReviewItem;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;

@FeignClient(name = "order-service", path = "/internal/orders")
public interface OrderClient {

	@GetMapping("/reviews/{spuId}")
	Result<List<ReviewItem>> reviews(@PathVariable("spuId") Long spuId);

	@PostMapping("/ratings")
	Result<Map<Long, BigDecimal>> ratings(@RequestBody List<Long> spuIds);

	@PostMapping("/merchant-aggregates")
	Result<MerchantAggregateResponse> merchantAggregates(@RequestBody MerchantAggregatesRequest request);
}
