package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.client.ProductDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseItem;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.entity.model.CheckoutOp;
import com.edu.csu.web.pet_shop_backend.entity.model.Order;
import com.edu.csu.web.pet_shop_backend.entity.model.OrderInventoryOp;
import com.edu.csu.web.pet_shop_backend.mapper.OrderInventoryOpMapper;
import com.edu.csu.web.pet_shop_backend.mapper.OrderMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CheckoutCompensationJob {

	private final CheckoutOpService checkoutOpService;
	private final OrderInventoryOpMapper orderInventoryOpMapper;
	private final OrderMapper orderMapper;
	private final ProductDirectory productDirectory;
	private final ObjectMapper objectMapper;

	public CheckoutCompensationJob(CheckoutOpService checkoutOpService,
			OrderInventoryOpMapper orderInventoryOpMapper,
			OrderMapper orderMapper,
			ProductDirectory productDirectory,
			ObjectMapper objectMapper) {
		this.checkoutOpService = checkoutOpService;
		this.orderInventoryOpMapper = orderInventoryOpMapper;
		this.orderMapper = orderMapper;
		this.productDirectory = productDirectory;
		this.objectMapper = objectMapper;
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
	public void compensateCheckoutOps() {
		List<CheckoutOp> stale = checkoutOpService.findStale(2);
		for (CheckoutOp operation : stale) {
			try {
				Long orderCount = orderMapper.selectCount(
					new LambdaQueryWrapper<Order>().eq(Order::getCheckoutOpId, operation.getOpId()));
				if (orderCount != null && orderCount > 0) {
					checkoutOpService.mark(operation.getOpId(), "COMMITTED",
						operation.getReserveResultJson(), null);
					continue;
				}
				if (operation.getReserveResultJson() == null) {
					// reserve 结果未知且没有本地订单：不盲目释放，留给人工/重试
					checkoutOpService.mark(operation.getOpId(), "FAILED",
						operation.getReserveResultJson(), "reserve result unknown");
					continue;
				}
				InventoryReleaseRequest request = new InventoryReleaseRequest();
				request.setOpId(operation.getOpId());
				request.setReason("checkout compensation job");
				productDirectory.release(request);
				checkoutOpService.mark(operation.getOpId(), "COMPENSATED",
					operation.getReserveResultJson(), null);
			} catch (Exception ex) {
				checkoutOpService.mark(operation.getOpId(), "COMPENSATING",
					operation.getReserveResultJson(), ex.getMessage());
			}
		}
	}

	@Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
	public void retryOrderInventoryOps() {
		List<OrderInventoryOp> pending = orderInventoryOpMapper.selectPending(50);
		for (OrderInventoryOp operation : pending) {
			try {
				List<InventoryReleaseItem> items = objectMapper.readValue(
					operation.getItemsJson(), new TypeReference<List<InventoryReleaseItem>>() {
					});
				InventoryReleaseRequest request = new InventoryReleaseRequest();
				request.setOpId(operation.getOpId());
				request.setItems(items);
				request.setReason("order inventory retry");
				productDirectory.release(request);
				operation.setStatus("DONE");
				operation.setLastError(null);
				orderInventoryOpMapper.update(operation);
			} catch (Exception ex) {
				operation.setStatus("FAILED");
				operation.setLastError(ex.getMessage());
				orderInventoryOpMapper.update(operation);
			}
		}
	}
}
