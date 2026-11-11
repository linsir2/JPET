package com.edu.csu.web.pet_shop_backend.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.edu.csu.web.pet_shop_backend.client.ProductDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.CheckoutItem;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.CheckoutRequest;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.CheckoutItemSnapshot;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.InventoryOpView;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerCheckoutDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerCheckoutItemDto;
import com.edu.csu.web.pet_shop_backend.entity.model.Order;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerCheckoutVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 下单编排（非事务）：
 * RESERVING -> product reserve -> RESERVED -> 本地写单 -> COMMITTED；
 * 失败/未知 -> COMPENSATING -> release -> COMPENSATED。
 */
@Service
public class OrderCheckoutOrchestrator {

	private static final DateTimeFormatter ORDER_NO_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private final ProductDirectory productDirectory;
	private final CheckoutOpService checkoutOpService;
	private final OrderWriter orderWriter;
	private final ObjectMapper objectMapper;

	public OrderCheckoutOrchestrator(ProductDirectory productDirectory,
			CheckoutOpService checkoutOpService,
			OrderWriter orderWriter,
			ObjectMapper objectMapper) {
		this.productDirectory = productDirectory;
		this.checkoutOpService = checkoutOpService;
		this.orderWriter = orderWriter;
		this.objectMapper = objectMapper;
	}

	public BuyerCheckoutVo checkout(BuyerCheckoutDto dto, Long userId) {
		if (userId == null || userId <= 0) {
			throw BusinessException.unauthorized("请先登录后再下单");
		}
		if (dto.getItems() == null || dto.getItems().isEmpty()) {
			throw BusinessException.badRequest("请至少选择一件商品后再提交订单");
		}

		String opId = "checkout-" + UUID.randomUUID().toString().replace("-", "");
		CheckoutRequest request = buildRequest(dto, userId, opId);
		String itemsJson = write(request.getItems());
		checkoutOpService.createReserving(opId, userId, itemsJson);

		try {
			List<CheckoutItemSnapshot> snapshots = productDirectory.reserve(request);
			if (snapshots == null || snapshots.isEmpty()) {
				throw BusinessException.conflict("库存预留结果为空");
			}
			String reserveJson = write(snapshots);
			checkoutOpService.mark(opId, "RESERVED", reserveJson, null);
			List<Order> orders = buildOrders(dto, userId, opId, snapshots);
			orderWriter.insertOrders(orders);
			checkoutOpService.mark(opId, "COMMITTED", reserveJson, null);
			return new BuyerCheckoutVo(orders.get(0).getOrderNo());
		} catch (BusinessException ex) {
			checkoutOpService.mark(opId, "FAILED", null, ex.getMessage());
			throw ex;
		} catch (Exception ex) {
			return recoverUnknownResult(dto, userId, opId, ex);
		}
	}

	private BuyerCheckoutVo recoverUnknownResult(BuyerCheckoutDto dto, Long userId, String opId, Exception cause) {
		InventoryOpView view = null;
		try {
			view = productDirectory.getOp(opId, "reserve");
		} catch (Exception ignored) {
			// product-service unreachable; leave FAILED for manual compensation
		}
		if (view == null || !"RESERVED".equals(view.getStatus())) {
			checkoutOpService.mark(opId, "FAILED", null, cause.getMessage());
			throw BusinessException.conflict("下单失败，请稍后重试");
		}

		List<CheckoutItemSnapshot> snapshots = view.getSnapshots() == null ? List.of() : view.getSnapshots();
		String reserveJson = write(snapshots);
		try {
			List<Order> orders = buildOrders(dto, userId, opId, snapshots);
			orderWriter.insertOrders(orders);
			checkoutOpService.mark(opId, "COMMITTED", reserveJson, null);
			return new BuyerCheckoutVo(orders.get(0).getOrderNo());
		} catch (Exception insertEx) {
			checkoutOpService.mark(opId, "COMPENSATING", reserveJson, insertEx.getMessage());
			try {
				release(opId);
				checkoutOpService.mark(opId, "COMPENSATED", reserveJson, insertEx.getMessage());
			} catch (Exception releaseEx) {
				checkoutOpService.mark(opId, "COMPENSATING", reserveJson, releaseEx.getMessage());
			}
			throw BusinessException.conflict("下单失败，请稍后重试");
		}
	}

	private void release(String opId) {
		InventoryReleaseRequest request = new InventoryReleaseRequest();
		request.setOpId(opId);
		request.setReason("checkout compensation");
		productDirectory.release(request);
	}

	private CheckoutRequest buildRequest(BuyerCheckoutDto dto, Long userId, String opId) {
		CheckoutRequest request = new CheckoutRequest();
		request.setOpId(opId);
		request.setUserId(userId);
		List<CheckoutItem> items = new ArrayList<>();
		int index = 0;
		for (BuyerCheckoutItemDto item : dto.getItems()) {
			if (item == null) {
				continue;
			}
			CheckoutItem target = new CheckoutItem();
			target.setLineId(String.valueOf(index++));
			target.setSpuId(parseId(item.getProductId()));
			target.setSelection(item.getSelection());
			target.setQuantity(item.getQuantity());
			items.add(target);
		}
		request.setItems(items);
		return request;
	}

	private List<Order> buildOrders(BuyerCheckoutDto dto, Long userId, String opId,
			List<CheckoutItemSnapshot> snapshots) {
		if (snapshots == null || snapshots.isEmpty()) {
			throw BusinessException.conflict("库存预留结果为空");
		}
		List<Order> orders = new ArrayList<>();
		for (CheckoutItemSnapshot snapshot : snapshots) {
			Order order = new Order();
			order.setOrderNo(generateOrderNo());
			order.setUserId(userId);
			order.setSpuId(snapshot.getSpuId());
			order.setSpuName(snapshot.getSpuName());
			order.setSkuId(snapshot.getSkuId());
			order.setSkuAttributeValues(objectMapper.valueToTree(snapshot.getSkuAttributes()));
			order.setQuantity(snapshot.getQuantity());
			order.setTotalPrice(snapshot.getUnitPrice().multiply(BigDecimal.valueOf(snapshot.getQuantity())));
			order.setStatus("pending");
			order.setBuyerDeleted(false);
			order.setCheckoutOpId(opId);
			order.setReceiverName(dto.getReceiver());
			order.setReceiverPhone(dto.getPhone());
			order.setReceiverAddress(dto.getAddress());
			order.setPaymentMethod(dto.getPaymentMethod());
			order.setShippingProgress("待发货");
			order.setCurrentNode("商家待处理");
			order.setMapX(BigDecimal.valueOf(18));
			order.setMapY(BigDecimal.valueOf(52));
			orders.add(order);
		}
		return orders;
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw BusinessException.badRequest("请求序列化失败");
		}
	}

	private Long parseId(String raw) {
		try {
			return Long.valueOf(raw);
		} catch (Exception ex) {
			throw BusinessException.badRequest("商品ID无效");
		}
	}

	private String generateOrderNo() {
		return "PS" + LocalDateTime.now().format(ORDER_NO_FORMAT)
			+ UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
	}
}
