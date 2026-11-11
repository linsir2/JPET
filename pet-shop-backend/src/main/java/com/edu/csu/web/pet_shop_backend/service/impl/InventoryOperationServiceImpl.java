package com.edu.csu.web.pet_shop_backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.model.InventoryOperation;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSKU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutItem;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutItemSnapshot;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutRequest;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryOpView;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryReleaseItem;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.mapper.InventoryOperationMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSKUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.service.InventoryOperationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class InventoryOperationServiceImpl implements InventoryOperationService {

	private static final String OP_RESERVE = "reserve";
	private static final String OP_RELEASE = "release";
	private static final String STATUS_RESERVED = "RESERVED";
	private static final String STATUS_RELEASED = "RELEASED";

	private final InventoryOperationMapper inventoryOperationMapper;
	private final ProductSPUMapper productSPUMapper;
	private final ProductSKUMapper productSKUMapper;
	private final ObjectMapper objectMapper;

	public InventoryOperationServiceImpl(InventoryOperationMapper inventoryOperationMapper,
			ProductSPUMapper productSPUMapper,
			ProductSKUMapper productSKUMapper,
			ObjectMapper objectMapper) {
		this.inventoryOperationMapper = inventoryOperationMapper;
		this.productSPUMapper = productSPUMapper;
		this.productSKUMapper = productSKUMapper;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<CheckoutItemSnapshot> reserve(CheckoutRequest request) {
		if (request == null || normalize(request.getOpId()) == null) {
			throw BusinessException.badRequest("opId 不能为空");
		}
		if (request.getItems() == null || request.getItems().isEmpty()) {
			throw BusinessException.badRequest("请至少选择一个商品");
		}

		InventoryOperation existing = inventoryOperationMapper.selectOne(request.getOpId(), OP_RESERVE);
		if (existing != null) {
			if (STATUS_RESERVED.equals(existing.getStatus())) {
				return readSnapshots(existing.getResultJson());
			}
			throw BusinessException.conflict("该库存预留已释放，不能重复使用");
		}

		List<CheckoutItemSnapshot> snapshots = new ArrayList<>();
		for (CheckoutItem item : request.getItems()) {
			if (item == null || item.getSpuId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
				throw BusinessException.badRequest("商品项参数无效");
			}
			ProductSPU spu = productSPUMapper.selectById(item.getSpuId());
			if (spu == null || spu.getIsActive() == null || spu.getIsActive() != 1 || !"published".equals(spu.getStatus())) {
				throw BusinessException.notFound("商品不存在或已下架");
			}
			ProductSKU sku = matchSku(item.getSpuId(), item.getSelection());
			if (sku == null) {
				throw BusinessException.notFound("未找到对应商品规格");
			}
			int updated = productSKUMapper.deductStock(sku.getId(), item.getQuantity());
			if (updated == 0) {
				throw BusinessException.conflict("商品库存不足：" + spu.getName());
			}
			productSPUMapper.incrementSales(item.getSpuId(), item.getQuantity());
			snapshots.add(new CheckoutItemSnapshot(
				item.getLineId(),
				item.getSpuId(),
				spu.getName(),
				sku.getId(),
				item.getQuantity(),
				sku.getPrice(),
				effectiveAttributes(item.getSelection(), sku)
			));
		}

		InventoryOperation operation = new InventoryOperation();
		operation.setOpId(request.getOpId());
		operation.setOpType(OP_RESERVE);
		operation.setStatus(STATUS_RESERVED);
		operation.setUserId(request.getUserId());
		operation.setResultJson(writeSnapshots(snapshots));
		try {
			inventoryOperationMapper.insert(operation);
		} catch (DuplicateKeyException ex) {
			throw BusinessException.conflict("重复的库存预留请求");
		}
		return snapshots;
	}

	@Override
	public InventoryOpView getOp(String opId, String opType) {
		String type = normalize(opType) == null ? OP_RESERVE : opType;
		InventoryOperation operation = inventoryOperationMapper.selectOne(opId, type);
		if (operation == null) {
			return null;
		}
		InventoryOpView view = new InventoryOpView();
		view.setOpId(operation.getOpId());
		view.setOpType(operation.getOpType());
		view.setStatus(operation.getStatus());
		if (OP_RESERVE.equals(type)) {
			view.setSnapshots(readSnapshots(operation.getResultJson()));
		}
		return view;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void release(InventoryReleaseRequest request) {
		if (request == null || normalize(request.getOpId()) == null) {
			throw BusinessException.badRequest("opId 不能为空");
		}

		InventoryOperation existing = inventoryOperationMapper.selectOne(request.getOpId(), OP_RELEASE);
		if (existing != null && STATUS_RELEASED.equals(existing.getStatus())) {
			return;
		}

		List<InventoryReleaseItem> items = request.getItems();
		InventoryOperation reserveOp = null;
		if (items == null || items.isEmpty()) {
			reserveOp = inventoryOperationMapper.selectOne(request.getOpId(), OP_RESERVE);
			if (reserveOp == null || !STATUS_RESERVED.equals(reserveOp.getStatus())) {
				// 没有可释放的预留：幂等返回
				return;
			}
			items = readSnapshots(reserveOp.getResultJson()).stream()
				.map(snapshot -> {
					InventoryReleaseItem item = new InventoryReleaseItem();
					item.setSpuId(snapshot.getSpuId());
					item.setSkuId(snapshot.getSkuId());
					item.setQuantity(snapshot.getQuantity());
					return item;
				})
				.toList();
		}

		for (InventoryReleaseItem item : items) {
			if (item == null || item.getSkuId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
				throw BusinessException.badRequest("库存释放项参数无效");
			}
			productSKUMapper.restoreStock(item.getSkuId(), item.getQuantity());
			if (item.getSpuId() != null) {
				productSPUMapper.decrementSales(item.getSpuId(), item.getQuantity());
			}
		}

		if (reserveOp != null) {
			reserveOp.setStatus(STATUS_RELEASED);
			inventoryOperationMapper.update(reserveOp);
		}

		InventoryOperation releaseOp = new InventoryOperation();
		releaseOp.setOpId(request.getOpId());
		releaseOp.setOpType(OP_RELEASE);
		releaseOp.setStatus(STATUS_RELEASED);
		if (existing == null) {
			inventoryOperationMapper.insert(releaseOp);
		} else {
			inventoryOperationMapper.update(releaseOp);
		}
	}

	private ProductSKU matchSku(Long spuId, Map<String, String> selection) {
		List<ProductSKU> skus = productSKUMapper.selectList(new LambdaQueryWrapper<ProductSKU>()
			.eq(ProductSKU::getSpuId, spuId)
			.eq(ProductSKU::getIsActive, 1)
			.eq(ProductSKU::getStatus, "available")
			.orderByAsc(ProductSKU::getPrice));
		if (skus.isEmpty()) {
			return null;
		}
		if (selection == null || selection.isEmpty()) {
			return skus.get(0);
		}
		for (ProductSKU sku : skus) {
			boolean matched = true;
			for (Map.Entry<String, String> entry : selection.entrySet()) {
				String expected = normalize(entry.getValue());
				if (expected == null) {
					continue;
				}
				JsonNode attrs = sku.getAttributeValues();
				String actual = attrs != null && attrs.has(entry.getKey())
					? normalize(attrs.get(entry.getKey()).asText())
					: null;
				if (!Objects.equals(expected, actual)) {
					matched = false;
					break;
				}
			}
			if (matched) {
				return sku;
			}
		}
		throw BusinessException.conflict("未找到对应商品规格");
	}

	private Map<String, String> effectiveAttributes(Map<String, String> selection, ProductSKU sku) {
		Map<String, String> result = new LinkedHashMap<>();
		if (selection != null && !selection.isEmpty()) {
			for (Map.Entry<String, String> entry : selection.entrySet()) {
				String value = normalizeNullable(entry.getValue());
				if (value != null) {
					result.put(entry.getKey(), value);
				}
			}
			if (!result.isEmpty()) {
				return result;
			}
		}
		JsonNode attrs = sku.getAttributeValues();
		if (attrs != null && attrs.isObject()) {
			attrs.fields().forEachRemaining(entry -> {
				String value = normalizeNullable(entry.getValue() == null ? null : entry.getValue().asText());
				if (value != null) {
					result.put(entry.getKey(), value);
				}
			});
		}
		return result;
	}

	private String writeSnapshots(List<CheckoutItemSnapshot> snapshots) {
		try {
			return objectMapper.writeValueAsString(snapshots);
		} catch (Exception ex) {
			throw BusinessException.badRequest("库存快照序列化失败");
		}
	}

	private List<CheckoutItemSnapshot> readSnapshots(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<List<CheckoutItemSnapshot>>() {
			});
		} catch (Exception ex) {
			throw BusinessException.badRequest("库存快照解析失败");
		}
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String normalize(String value) {
		String normalized = normalizeNullable(value);
		return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
	}
}
