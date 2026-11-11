package com.edu.csu.web.pet_shop_backend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.model.Order;
import com.edu.csu.web.pet_shop_backend.internal.InternalOrderContracts.MerchantAggregateResponse;
import com.edu.csu.web.pet_shop_backend.internal.InternalOrderContracts.MerchantAggregatesRequest;
import com.edu.csu.web.pet_shop_backend.internal.InternalOrderContracts.ReviewItem;
import com.edu.csu.web.pet_shop_backend.mapper.OrderMapper;

/**
 * order-service 内部接口，供 product-service 展示评价/评分/商家营收。
 */
@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final String STATUS_COMPLETED = "completed";
	private static final String STATUS_CANCELLED = "cancelled";

	private final OrderMapper orderMapper;

	public InternalOrderController(OrderMapper orderMapper) {
		this.orderMapper = orderMapper;
	}

	@GetMapping("/reviews/{spuId}")
	public Result<List<ReviewItem>> reviews(@PathVariable("spuId") Long spuId) {
		List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
			.eq(Order::getSpuId, spuId)
			.eq(Order::getStatus, STATUS_COMPLETED)
			.isNotNull(Order::getReviewTime)
			.orderByDesc(Order::getReviewTime));
		List<ReviewItem> result = orders.stream()
			.map(order -> new ReviewItem(
				order.getId(),
				order.getUserId(),
				order.getReviewRating(),
				order.getReviewContent(),
				format(order.getReviewTime())))
			.toList();
		return Result.success(result);
	}

	@PostMapping("/ratings")
	public Result<Map<Long, BigDecimal>> ratings(@RequestBody List<Long> spuIds) {
		if (spuIds == null || spuIds.isEmpty()) {
			return Result.success(Map.of());
		}
		List<Long> distinctSpuIds = spuIds.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctSpuIds.isEmpty()) {
			return Result.success(Map.of());
		}
		List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
			.in(Order::getSpuId, distinctSpuIds)
			.eq(Order::getStatus, STATUS_COMPLETED)
			.isNotNull(Order::getReviewRating));
		Map<Long, List<Integer>> grouped = new LinkedHashMap<>();
		for (Order order : orders) {
			if (order.getSpuId() == null || order.getReviewRating() == null) {
				continue;
			}
			grouped.computeIfAbsent(order.getSpuId(), key -> new ArrayList<>()).add(order.getReviewRating());
		}
		Map<Long, BigDecimal> result = new LinkedHashMap<>();
		grouped.forEach((spuId, values) -> {
			double average = values.stream().mapToInt(Integer::intValue).average().orElse(0D);
			result.put(spuId, BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP));
		});
		return Result.success(result);
	}

	@PostMapping("/merchant-aggregates")
	public Result<MerchantAggregateResponse> merchantAggregates(@RequestBody MerchantAggregatesRequest request) {
		MerchantAggregateResponse response = new MerchantAggregateResponse();
		if (request == null || request.getSpuIds() == null || request.getSpuIds().isEmpty()) {
			response.setAverageRating(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
			return Result.success(response);
		}
		List<Long> spuIds = request.getSpuIds().stream().filter(Objects::nonNull).distinct().toList();
		if (spuIds.isEmpty()) {
			response.setAverageRating(BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP));
			return Result.success(response);
		}

		LocalDateTime currentStart = parseOr(request.getCurrentMonthStart(), monthStart(0));
		LocalDateTime nextStart = parseOr(request.getNextMonthStart(), monthStart(1));
		LocalDateTime previousStart = parseOr(request.getPreviousMonthStart(), monthStart(-1));

		List<Order> billable = orderMapper.selectList(new LambdaQueryWrapper<Order>()
			.in(Order::getSpuId, spuIds)
			.ne(Order::getStatus, STATUS_CANCELLED));

		BigDecimal current = sum(billable, currentStart, nextStart);
		BigDecimal previous = sum(billable, previousStart, currentStart);
		BigDecimal total = billable.stream()
			.map(Order::getTotalPrice)
			.filter(Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		List<Order> rated = orderMapper.selectList(new LambdaQueryWrapper<Order>()
			.in(Order::getSpuId, spuIds)
			.eq(Order::getStatus, STATUS_COMPLETED)
			.isNotNull(Order::getReviewRating));
		BigDecimal average = rated.isEmpty()
			? BigDecimal.ZERO
			: BigDecimal.valueOf(rated.stream()
				.map(Order::getReviewRating)
				.filter(Objects::nonNull)
				.mapToInt(Integer::intValue)
				.average()
				.orElse(0D));

		response.setCurrentRevenue(current);
		response.setPreviousRevenue(previous);
		response.setTotalRevenue(total);
		response.setAverageRating(average.setScale(1, RoundingMode.HALF_UP));
		return Result.success(response);
	}

	private BigDecimal sum(List<Order> orders, LocalDateTime start, LocalDateTime end) {
		return orders.stream()
			.filter(order -> order.getCreateTime() != null)
			.filter(order -> !order.getCreateTime().isBefore(start) && order.getCreateTime().isBefore(end))
			.map(Order::getTotalPrice)
			.filter(Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private LocalDateTime monthStart(int offsetMonths) {
		return LocalDate.now().plusMonths(offsetMonths).withDayOfMonth(1).atStartOfDay();
	}

	private LocalDateTime parseOr(String raw, LocalDateTime fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return LocalDateTime.parse(raw);
		} catch (Exception ex) {
			return fallback;
		}
	}

	private String format(LocalDateTime value) {
		return value == null ? "" : value.format(DATE_TIME);
	}
}
