package com.edu.csu.web.pet_shop_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerOrderAddressDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerCheckoutDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerOrderReviewDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerCheckoutVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerOrderVo;
import com.edu.csu.web.pet_shop_backend.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping("/checkout")
	public Result<BuyerCheckoutVo> checkout(
		@Valid @RequestBody BuyerCheckoutDto dto,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.checkout(dto, userId));
	}

	@GetMapping
	public Result<Map<String, Object>> listOrders(
		@RequestParam(value = "status", required = false) String status,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.listBuyerOrders(userId, status));
	}

	@PutMapping("/{id}/cancel")
	public Result<BuyerOrderVo> cancelOrder(
		@PathVariable("id") Long orderId,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.cancelOrder(orderId, userId));
	}

	@PutMapping("/{id}/confirm")
	public Result<BuyerOrderVo> confirmReceived(
		@PathVariable("id") Long orderId,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.confirmReceived(orderId, userId));
	}

	@PutMapping("/{id}/address")
	public Result<BuyerOrderVo> updateAddress(
		@PathVariable("id") Long orderId,
		@Valid @RequestBody BuyerOrderAddressDto dto,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.updateOrderAddress(orderId, dto, userId));
	}

	@PutMapping("/{id}/return")
	public Result<BuyerOrderVo> requestReturn(
		@PathVariable("id") Long orderId,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		return Result.success(orderService.requestReturn(orderId, userId));
	}

	@PostMapping("/{id}/review")
	public Result<BuyerOrderVo> reviewOrder(
		@PathVariable("id") Long orderId,
		@Valid @RequestBody BuyerOrderReviewDto dto,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		String username = jwtUserContext == null ? null : jwtUserContext.getUsername();
		return Result.success(orderService.reviewOrder(orderId, dto, userId, username));
	}

	@DeleteMapping("/{id}")
	public Result<Void> deleteOrder(
		@PathVariable("id") Long orderId,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		orderService.deleteOrder(orderId, userId);
		return Result.<Void>success("deleted", null);
	}

	@DeleteMapping("/{id}/review")
	public Result<Void> deleteReview(
		@PathVariable("id") Long orderId,
		@InjectJWTUserContext JWTUserContext jwtUserContext
	) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		orderService.deleteReview(orderId, userId);
		return Result.<Void>success("deleted", null);
	}
}