package com.edu.csu.web.pet_shop_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.service.OrderService;

@RestController
@RequestMapping("/api/merchant/orders")
public class MerchantOrderController {

	private final OrderService orderService;

	public MerchantOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@GetMapping
	public Result<Map<String, Object>> list(
		@RequestParam(required = false) String status,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.listMerchantOrders(userId, status, false));
	}

	@GetMapping("/history")
	public Result<Map<String, Object>> listHistory(
		@RequestParam(required = false) String status,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.listMerchantOrders(userId, status, true));
	}

	@PutMapping("/{id}/ship")
	public Result<?> markShipped(
		@PathVariable("id") Long orderId,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.markOrderShipped(orderId, userId));
	}
}