package com.edu.csu.web.pet_shop_backend.client;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.MerchantAggregateResponse;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.MerchantAggregatesRequest;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.ReviewItem;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;

@Component
public class OrderDirectory {

	private final OrderClient orderClient;

	public OrderDirectory(OrderClient orderClient) {
		this.orderClient = orderClient;
	}

	public List<ReviewItem> reviews(Long spuId) {
		Result<List<ReviewItem>> result = orderClient.reviews(spuId);
		ensureSuccess(result);
		return result == null || result.getData() == null ? List.of() : result.getData();
	}

	public Map<Long, BigDecimal> ratings(Collection<Long> spuIds) {
		if (spuIds == null || spuIds.isEmpty()) {
			return Map.of();
		}
		List<Long> distinctIds = spuIds.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) {
			return Map.of();
		}
		Result<Map<Long, BigDecimal>> result = orderClient.ratings(distinctIds);
		ensureSuccess(result);
		return result == null || result.getData() == null ? Map.of() : result.getData();
	}

	public MerchantAggregateResponse merchantAggregates(Collection<Long> spuIds,
			String currentMonthStart, String nextMonthStart, String previousMonthStart) {
		if (spuIds == null || spuIds.isEmpty()) {
			MerchantAggregateResponse empty = new MerchantAggregateResponse();
			empty.setCurrentRevenue(BigDecimal.ZERO);
			empty.setPreviousRevenue(BigDecimal.ZERO);
			empty.setTotalRevenue(BigDecimal.ZERO);
			empty.setAverageRating(BigDecimal.ZERO);
			return empty;
		}
		MerchantAggregatesRequest request = new MerchantAggregatesRequest();
		request.setSpuIds(spuIds.stream().filter(Objects::nonNull).distinct().toList());
		request.setCurrentMonthStart(currentMonthStart);
		request.setNextMonthStart(nextMonthStart);
		request.setPreviousMonthStart(previousMonthStart);
		Result<MerchantAggregateResponse> result = orderClient.merchantAggregates(request);
		ensureSuccess(result);
		return result == null || result.getData() == null ? new MerchantAggregateResponse() : result.getData();
	}

	private void ensureSuccess(Result<?> result) {
		if (result == null) {
			throw BusinessException.badRequest("order-service 返回为空");
		}
		if (result.getCode() >= 400) {
			throw new BusinessException(result.getCode(), result.getMessage());
		}
	}
}
