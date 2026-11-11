package com.edu.csu.web.pet_shop_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.csu.web.pet_shop_backend.client.ProductDirectory;
import com.edu.csu.web.pet_shop_backend.client.UserDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseItem;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.CatalogSpu;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.MerchantCatalog;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.SpuBrief;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerCheckoutDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerOrderAddressDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerOrderReviewDto;
import com.edu.csu.web.pet_shop_backend.entity.model.Order;
import com.edu.csu.web.pet_shop_backend.entity.model.OrderInventoryOp;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerCheckoutVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerOrderReviewVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerOrderVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantOrderVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.mapper.OrderInventoryOpMapper;
import com.edu.csu.web.pet_shop_backend.mapper.OrderMapper;
import com.edu.csu.web.pet_shop_backend.service.OrderCheckoutOrchestrator;
import com.edu.csu.web.pet_shop_backend.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class OrderServiceImpl implements OrderService {

	private static final String ORDER_STATUS_PENDING = "pending";
	private static final String ORDER_STATUS_SHIPPED = "shipped";
	private static final String ORDER_STATUS_COMPLETED = "completed";
	private static final String ORDER_STATUS_CANCELLED = "cancelled";
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final Pattern LEGACY_REGISTERED_MERCHANT_NAME = Pattern.compile("^(.*)_merchant_\\d+$");
	private static final String DEFAULT_USER_AVATAR = "/images/logo192.png";

	private final OrderMapper orderMapper;
	private final ProductDirectory productDirectory;
	private final UserDirectory userDirectory;
	private final OrderCheckoutOrchestrator checkoutOrchestrator;
	private final OrderInventoryOpMapper orderInventoryOpMapper;
	private final ObjectMapper objectMapper;

	public OrderServiceImpl(OrderMapper orderMapper,
			ProductDirectory productDirectory,
			UserDirectory userDirectory,
			OrderCheckoutOrchestrator checkoutOrchestrator,
			OrderInventoryOpMapper orderInventoryOpMapper,
			ObjectMapper objectMapper) {
		this.orderMapper = orderMapper;
		this.productDirectory = productDirectory;
		this.userDirectory = userDirectory;
		this.checkoutOrchestrator = checkoutOrchestrator;
		this.orderInventoryOpMapper = orderInventoryOpMapper;
		this.objectMapper = objectMapper;
	}

	@Override
	public BuyerCheckoutVo checkout(BuyerCheckoutDto dto, Long userId) {
		return checkoutOrchestrator.checkout(dto, userId);
	}

	@Override
	public Map<String, Object> listBuyerOrders(Long userId, String status) {
		if (userId == null || userId <= 0) {
			throw BusinessException.unauthorized("请先登录后查看订单");
		}
		String normalizedStatus = normalizeBuyerStatus(status);
		LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
			.eq(Order::getUserId, userId)
			.eq(Order::getBuyerDeleted, false)
			.orderByDesc(Order::getCreateTime);
		if (normalizedStatus != null) {
			query.eq(Order::getStatus, normalizedStatus);
		}
		List<Order> orders = orderMapper.selectList(query);
		if (orders.isEmpty()) {
			return pageResult(List.of());
		}

		List<Long> spuIds = orders.stream().map(Order::getSpuId).filter(Objects::nonNull).distinct().toList();
		Map<Long, SpuBrief> spuMap = productDirectory.spusBatch(spuIds);
		Map<Long, UserBriefVo> userMap = userDirectory.batch(collectUserIds(orders, spuMap.values()));

		List<BuyerOrderVo> list = orders.stream()
			.map(order -> toBuyerOrderVo(order, spuMap.get(order.getSpuId()), userMap))
			.toList();
		return pageResult(list);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrderVo cancelOrder(Long orderId, Long userId) {
		Order order = loadBuyerOwnedOrder(orderId, userId);
		if (!ORDER_STATUS_PENDING.equals(order.getStatus())) {
			throw BusinessException.conflict("只有待发货订单可以取消");
		}
		order.setStatus(ORDER_STATUS_CANCELLED);
		order.setCancelReason("买家主动取消订单");
		order.setShippingProgress("订单已取消");
		order.setCurrentNode("买家已取消订单");
		orderMapper.updateById(order);
		requestInventoryRelease(order, "cancel");
		return toBuyerVo(orderMapper.selectById(orderId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrderVo confirmReceived(Long orderId, Long userId) {
		Order order = loadBuyerOwnedOrder(orderId, userId);
		if (!ORDER_STATUS_SHIPPED.equals(order.getStatus())) {
			throw BusinessException.conflict("只有已发货订单可以确认收货");
		}
		order.setStatus(ORDER_STATUS_COMPLETED);
		order.setShippingProgress("订单已完成");
		order.setCurrentNode("买家已签收");
		order.setCompletedTime(LocalDateTime.now());
		order.setMapX(BigDecimal.valueOf(82));
		order.setMapY(BigDecimal.valueOf(50));
		orderMapper.updateById(order);
		return toBuyerVo(orderMapper.selectById(orderId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrderVo updateOrderAddress(Long orderId, BuyerOrderAddressDto dto, Long userId) {
		Order order = loadBuyerOwnedOrder(orderId, userId);
		if (!ORDER_STATUS_PENDING.equals(order.getStatus())) {
			throw BusinessException.conflict("只有待发货订单可以修改地址");
		}
		order.setReceiverAddress(dto.getAddress().trim());
		orderMapper.updateById(order);
		return toBuyerVo(orderMapper.selectById(orderId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrderVo requestReturn(Long orderId, Long userId) {
		Order order = loadBuyerOwnedOrder(orderId, userId);
		if (!ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
			throw BusinessException.conflict("只有已完成订单可以申请退货");
		}
		order.setStatus(ORDER_STATUS_CANCELLED);
		order.setCancelReason("买家申请退货");
		order.setShippingProgress("退货申请中");
		order.setCurrentNode("等待商家处理退货申请");
		orderMapper.updateById(order);
		requestInventoryRelease(order, "return");
		return toBuyerVo(orderMapper.selectById(orderId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrderVo reviewOrder(Long orderId, BuyerOrderReviewDto dto, Long userId, String username) {
		Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
			.eq(Order::getId, orderId)
			.eq(Order::getUserId, userId)
			.eq(Order::getBuyerDeleted, false));
		if (order == null) {
			throw BusinessException.notFound("订单不存在");
		}
		if (!ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
			throw BusinessException.conflict("仅已完成订单可以评价");
		}
		if (order.getReviewTime() != null) {
			throw BusinessException.conflict("该订单已评价，无需重复提交");
		}
		order.setReviewRating(dto.getRating());
		order.setReviewContent(dto.getContent().trim());
		order.setReviewTime(LocalDateTime.now());
		orderMapper.updateById(order);
		return toBuyerVo(orderMapper.selectById(orderId));
	}

	@Override
	public void deleteOrder(Long orderId, Long userId) {
		Order order = loadBuyerOwnedOrderIncludeDeleted(orderId, userId);
		if (Boolean.TRUE.equals(order.getBuyerDeleted())) {
			return;
		}
		if (!ORDER_STATUS_CANCELLED.equals(order.getStatus()) && !ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
			throw BusinessException.conflict("只有已取消或已完成订单可以从历史中删除");
		}
		order.setBuyerDeleted(true);
		orderMapper.updateById(order);
	}

	@Override
	public void deleteReview(Long orderId, Long userId) {
		Order order = loadBuyerOwnedOrderIncludeDeleted(orderId, userId);
		if (!ORDER_STATUS_COMPLETED.equals(order.getStatus())) {
			throw BusinessException.conflict("只有已完成订单可以撤回评价");
		}
		if (order.getReviewTime() == null) {
			return;
		}
		orderMapper.update(null, new LambdaUpdateWrapper<Order>()
			.eq(Order::getId, orderId)
			.set(Order::getReviewRating, null)
			.set(Order::getReviewContent, null)
			.set(Order::getReviewTime, null));
	}

	@Override
	public Map<String, Object> listMerchantOrders(Long merchantUserId, String status, boolean history) {
		if (merchantUserId == null || merchantUserId <= 0) {
			throw BusinessException.unauthorized("请先登录商家账号");
		}
		MerchantCatalog catalog = productDirectory.catalog(merchantUserId);
		if (catalog == null) {
			throw BusinessException.forbidden("当前账号未绑定有效商家");
		}
		if (catalog.getSpus() == null || catalog.getSpus().isEmpty()) {
			return pageResult(List.of());
		}
		Map<Long, CatalogSpu> spuMap = catalog.getSpus().stream()
			.collect(Collectors.toMap(CatalogSpu::getId, item -> item, (left, right) -> left));
		List<Long> spuIds = new ArrayList<>(spuMap.keySet());

		String normalizedStatus = normalizeMerchantStatus(status);
		LambdaQueryWrapper<Order> query = new LambdaQueryWrapper<Order>()
			.in(Order::getSpuId, spuIds)
			.orderByDesc(Order::getCreateTime);
		if (normalizedStatus != null) {
			query.eq(Order::getStatus, normalizedStatus);
		} else if (history) {
			query.in(Order::getStatus, List.of(ORDER_STATUS_CANCELLED, ORDER_STATUS_COMPLETED));
		} else {
			query.in(Order::getStatus, List.of(ORDER_STATUS_PENDING, ORDER_STATUS_SHIPPED));
		}

		List<Order> orders = orderMapper.selectList(query);
		Map<Long, UserBriefVo> userMap = userDirectory.batch(
			orders.stream().map(Order::getUserId).filter(Objects::nonNull).distinct().toList());
		List<MerchantOrderVo> list = orders.stream()
			.map(order -> toMerchantOrderVo(order, spuMap.get(order.getSpuId()), userMap))
			.filter(Objects::nonNull)
			.toList();
		return pageResult(list);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public MerchantOrderVo markOrderShipped(Long orderId, Long merchantUserId) {
		if (merchantUserId == null || merchantUserId <= 0) {
			throw BusinessException.unauthorized("请先登录商家账号");
		}
		if (orderId == null || orderId <= 0) {
			throw BusinessException.badRequest("订单ID无效");
		}
		MerchantCatalog catalog = productDirectory.catalog(merchantUserId);
		if (catalog == null) {
			throw BusinessException.forbidden("当前账号未绑定有效商家");
		}
		Map<Long, CatalogSpu> spuMap = catalog.getSpus() == null ? Map.of() : catalog.getSpus().stream()
			.collect(Collectors.toMap(CatalogSpu::getId, item -> item, (left, right) -> left));

		Order order = orderMapper.selectById(orderId);
		if (order == null) {
			throw BusinessException.notFound("订单不存在");
		}
		if (order.getSpuId() == null || !spuMap.containsKey(order.getSpuId())) {
			throw BusinessException.forbidden("当前订单不属于该商家");
		}
		if (!ORDER_STATUS_PENDING.equals(order.getStatus())) {
			throw BusinessException.conflict("只有待发货订单可以标记为已寄出");
		}

		order.setStatus(ORDER_STATUS_SHIPPED);
		order.setTrackingNo(buildTrackingNo(order));
		order.setShippingProgress("已寄出");
		order.setCurrentNode("商家已发货，等待揽收");
		order.setMapX(BigDecimal.valueOf(44));
		order.setMapY(BigDecimal.valueOf(47));
		orderMapper.updateById(order);

		Map<Long, UserBriefVo> userMap = userDirectory.batch(List.of(order.getUserId()));
		return toMerchantOrderVo(orderMapper.selectById(orderId), spuMap.get(order.getSpuId()), userMap);
	}

	private BuyerOrderVo toBuyerVo(Order order) {
		if (order == null) {
			throw BusinessException.notFound("订单不存在");
		}
		SpuBrief spu = order.getSpuId() == null ? null
			: productDirectory.spusBatch(List.of(order.getSpuId())).get(order.getSpuId());
		Map<Long, UserBriefVo> userMap = userDirectory.batch(collectUserIds(List.of(order),
			spu == null ? List.of() : List.of(spu)));
		return toBuyerOrderVo(order, spu, userMap);
	}

	private List<Long> collectUserIds(Collection<Order> orders, Collection<SpuBrief> spus) {
		List<Long> ids = new ArrayList<>();
		orders.stream().map(Order::getUserId).filter(Objects::nonNull).forEach(ids::add);
		spus.stream().map(SpuBrief::getMerchantUserId).filter(Objects::nonNull).forEach(ids::add);
		return ids;
	}

	private MerchantOrderVo toMerchantOrderVo(Order order, CatalogSpu spu, Map<Long, UserBriefVo> userMap) {
		if (spu == null) {
			return null;
		}
		Map<String, String> skuAttributes = extractSkuAttributes(order);
		String destination = extractDestination(order.getReceiverAddress());
		UserBriefVo buyer = userMap.get(order.getUserId());
		String buyerName = displayName(buyer);
		String buyerAvatar = avatar(buyer);
		return new MerchantOrderVo(
			order.getId(),
			order.getSpuName(),
			spu.getImageUrl(),
			order.getTotalPrice(),
			resolveUnitPrice(order),
			order.getQuantity(),
			destination,
			order.getReceiverName(),
			order.getReceiverPhone(),
			order.getReceiverAddress(),
			buildSkuSummary(skuAttributes),
			skuAttributes,
			formatDateTime(order.getCreateTime()),
			order.getTrackingNo(),
			order.getCurrentNode(),
			defaultString(order.getShippingProgress(), merchantProgressText(order.getStatus())),
			order.getMapX(),
			order.getMapY(),
			formatDateTime(order.getUpdateTime()),
			order.getCancelReason(),
			order.getReceiverName(),
			order.getUserId(),
			buyerName,
			buyerAvatar,
			formatDateTime(order.getCompletedTime()),
			order.getReviewRating(),
			order.getReviewContent(),
			buyerName,
			formatDateTime(order.getReviewTime())
		);
	}

	private BuyerOrderVo toBuyerOrderVo(Order order, SpuBrief spu, Map<Long, UserBriefVo> userMap) {
		Map<String, String> skuAttributes = extractSkuAttributes(order);
		String reviewerName = displayName(userMap.get(order.getUserId()));
		Long merchantId = spu == null ? null : spu.getMerchantId();
		String merchantName = resolveMerchantDisplayName(spu, userMap);
		return new BuyerOrderVo(
			order.getId(),
			order.getOrderNo(),
			order.getSpuId(),
			order.getSpuName(),
			spu == null ? null : spu.getImageUrl(),
			order.getTotalPrice(),
			resolveUnitPrice(order),
			order.getQuantity(),
			order.getStatus(),
			defaultString(order.getShippingProgress(), buyerProgressText(order.getStatus())),
			defaultString(order.getCurrentNode(), buyerNodeText(order.getStatus())),
			order.getTrackingNo(),
			merchantId,
			merchantName,
			order.getPaymentMethod(),
			order.getReceiverName(),
			order.getReceiverPhone(),
			order.getReceiverAddress(),
			buildSkuSummary(skuAttributes),
			skuAttributes,
			formatDateTime(order.getCreateTime()),
			formatDateTime(order.getCompletedTime()),
			ORDER_STATUS_COMPLETED.equals(order.getStatus()) && order.getReviewTime() == null,
			toBuyerReviewVo(order, reviewerName)
		);
	}

	private String resolveMerchantDisplayName(SpuBrief spu, Map<Long, UserBriefVo> userMap) {
		if (spu == null) {
			return "商家";
		}
		String merchantName = cleanupLegacyMerchantName(spu.getMerchantName());
		if (merchantName != null) {
			return merchantName;
		}
		UserBriefVo user = spu.getMerchantUserId() == null ? null : userMap.get(spu.getMerchantUserId());
		String username = user == null ? null : cleanupLegacyMerchantName(user.getUsername());
		return username == null ? "商家" : username;
	}

	private BuyerOrderReviewVo toBuyerReviewVo(Order order, String reviewerName) {
		if (order.getReviewTime() == null) {
			return null;
		}
		return new BuyerOrderReviewVo(
			order.getReviewRating(),
			order.getReviewContent(),
			reviewerName,
			formatDateTime(order.getReviewTime()));
	}

	private void requestInventoryRelease(Order order, String action) {
		String opId = "release:" + order.getId() + ":" + action;
		InventoryReleaseItem item = new InventoryReleaseItem();
		item.setSpuId(order.getSpuId());
		item.setSkuId(order.getSkuId());
		item.setQuantity(order.getQuantity());

		OrderInventoryOp operation = new OrderInventoryOp();
		operation.setOpId(opId);
		operation.setOrderId(order.getId());
		operation.setAction(action);
		operation.setItemsJson(write(List.of(item)));
		operation.setStatus("PENDING");

		try {
			InventoryReleaseRequest request = new InventoryReleaseRequest();
			request.setOpId(opId);
			request.setItems(List.of(item));
			request.setReason(action);
			productDirectory.release(request);
			operation.setStatus("DONE");
			orderInventoryOpMapper.insert(operation);
		} catch (Exception ex) {
			operation.setStatus("FAILED");
			operation.setLastError(ex.getMessage());
			OrderInventoryOp existing = orderInventoryOpMapper.selectOne(opId);
			if (existing == null) {
				orderInventoryOpMapper.insert(operation);
			} else {
				orderInventoryOpMapper.update(operation);
			}
			// 不阻断取消/退货；补偿任务会重试
		}
	}

	private Order loadBuyerOwnedOrder(Long orderId, Long userId) {
		if (userId == null || userId <= 0) {
			throw BusinessException.unauthorized("请先登录后操作订单");
		}
		if (orderId == null || orderId <= 0) {
			throw BusinessException.badRequest("订单ID无效");
		}
		Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
			.eq(Order::getId, orderId)
			.eq(Order::getUserId, userId)
			.eq(Order::getBuyerDeleted, false));
		if (order == null) {
			throw BusinessException.notFound("订单不存在");
		}
		return order;
	}

	private Order loadBuyerOwnedOrderIncludeDeleted(Long orderId, Long userId) {
		if (userId == null || userId <= 0) {
			throw BusinessException.unauthorized("请先登录后操作订单");
		}
		Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
			.eq(Order::getId, orderId)
			.eq(Order::getUserId, userId));
		if (order == null) {
			throw BusinessException.notFound("订单不存在");
		}
		return order;
	}

	private Map<String, String> extractSkuAttributes(Order order) {
		Map<String, String> result = new LinkedHashMap<>();
		JsonNode raw = order.getSkuAttributeValues();
		if (raw == null || !raw.isObject()) {
			return result;
		}
		raw.fields().forEachRemaining(entry -> {
			String value = entry.getValue() == null ? "" : entry.getValue().asText("").trim();
			if (!value.isEmpty()) {
				result.put(entry.getKey(), value);
			}
		});
		return result;
	}

	private String buildSkuSummary(Map<String, String> skuAttributes) {
		if (skuAttributes == null || skuAttributes.isEmpty()) {
			return "默认规格";
		}
		return skuAttributes.entrySet().stream()
			.map(entry -> entry.getKey() + "=" + entry.getValue())
			.collect(Collectors.joining(" / "));
	}

	private BigDecimal resolveUnitPrice(Order order) {
		if (order.getTotalPrice() == null) {
			return BigDecimal.ZERO;
		}
		if (order.getQuantity() == null || order.getQuantity() <= 0) {
			return order.getTotalPrice();
		}
		return order.getTotalPrice().divide(BigDecimal.valueOf(order.getQuantity()), 2, RoundingMode.HALF_UP);
	}

	private String displayName(UserBriefVo user) {
		if (user == null || "deleted".equals(user.getStatus())) {
			return "匿名用户";
		}
		String username = normalize(user.getUsername());
		return username == null ? "匿名用户" : username;
	}

	private String avatar(UserBriefVo user) {
		if (user == null || user.getProfile() == null || user.getProfile().isBlank()) {
			return DEFAULT_USER_AVATAR;
		}
		try {
			JsonNode profileNode = objectMapper.readTree(user.getProfile());
			String avatar = profileNode == null ? null : normalize(profileNode.path("avatar").asText(null));
			return avatar == null ? DEFAULT_USER_AVATAR : avatar;
		} catch (Exception ex) {
			return DEFAULT_USER_AVATAR;
		}
	}

	private String cleanupLegacyMerchantName(String rawValue) {
		String normalized = normalize(rawValue);
		if (normalized == null) {
			return null;
		}
		Matcher matcher = LEGACY_REGISTERED_MERCHANT_NAME.matcher(normalized);
		if (matcher.matches()) {
			String prefix = normalize(matcher.group(1));
			return prefix == null ? normalized : prefix;
		}
		return normalized;
	}

	private Map<String, Object> pageResult(List<?> list) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("list", list);
		result.put("total", list.size());
		result.put("page", 1);
		result.put("size", list.isEmpty() ? 10 : list.size());
		return result;
	}

	private String normalizeMerchantStatus(String status) {
		String normalized = normalize(status);
		if (normalized == null) {
			return null;
		}
		return switch (normalized) {
			case "待发货" -> ORDER_STATUS_PENDING;
			case "已发货" -> ORDER_STATUS_SHIPPED;
			case "已取消" -> ORDER_STATUS_CANCELLED;
			case "已完成", "已到达" -> ORDER_STATUS_COMPLETED;
			case ORDER_STATUS_PENDING, ORDER_STATUS_SHIPPED, ORDER_STATUS_CANCELLED, ORDER_STATUS_COMPLETED -> normalized;
			default -> null;
		};
	}

	private String normalizeBuyerStatus(String status) {
		String normalized = normalize(status);
		if (normalized == null || Set.of("all", "全部").contains(normalized)) {
			return null;
		}
		return switch (normalized) {
			case "pending", "待发货" -> ORDER_STATUS_PENDING;
			case "shipped", "已发货", "运输中" -> ORDER_STATUS_SHIPPED;
			case "completed", "已完成", "已签收" -> ORDER_STATUS_COMPLETED;
			case "cancelled", "已取消" -> ORDER_STATUS_CANCELLED;
			default -> null;
		};
	}

	private String merchantProgressText(String status) {
		return switch (status) {
			case ORDER_STATUS_PENDING -> "待发货";
			case ORDER_STATUS_SHIPPED -> "运输中";
			case ORDER_STATUS_COMPLETED -> "已签收";
			case ORDER_STATUS_CANCELLED -> "已取消";
			default -> "处理中";
		};
	}

	private String buyerProgressText(String status) {
		return switch (status) {
			case ORDER_STATUS_PENDING -> "商家备货中";
			case ORDER_STATUS_SHIPPED -> "运输中";
			case ORDER_STATUS_COMPLETED -> "订单已完成";
			case ORDER_STATUS_CANCELLED -> "订单已取消";
			default -> "处理中";
		};
	}

	private String buyerNodeText(String status) {
		return switch (status) {
			case ORDER_STATUS_PENDING -> "等待商家发货";
			case ORDER_STATUS_SHIPPED -> "包裹配送中";
			case ORDER_STATUS_COMPLETED -> "已签收";
			case ORDER_STATUS_CANCELLED -> "订单关闭";
			default -> "处理中";
		};
	}

	private String extractDestination(String address) {
		String normalized = normalize(address);
		if (normalized == null) {
			return "待确认";
		}
		int suffix = Math.max(normalized.indexOf('市'), normalized.indexOf('区'));
		if (suffix > 0) {
			return normalized.substring(0, suffix + 1);
		}
		return normalized.length() > 12 ? normalized.substring(0, 12) : normalized;
	}

	private String buildTrackingNo(Order order) {
		String existing = normalize(order.getTrackingNo());
		if (existing != null) {
			return order.getTrackingNo();
		}
		String seed = order.getOrderNo() == null ? String.valueOf(order.getId()) : order.getOrderNo().replaceAll("[^0-9A-Za-z]", "");
		if (seed.length() > 14) {
			seed = seed.substring(seed.length() - 14);
		}
		return "YD" + seed;
	}

	private String formatDateTime(LocalDateTime value) {
		return value == null ? "" : value.format(DATE_TIME_FORMATTER);
	}

	private String defaultString(String raw, String fallback) {
		String normalized = normalize(raw);
		return normalized == null ? fallback : raw;
	}

	private String write(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception ex) {
			throw BusinessException.badRequest("序列化失败");
		}
	}

	private String normalize(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
	}
}
