package com.edu.csu.web.pet_shop_backend.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.model.Merchant;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CatalogSpu;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutItemSnapshot;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutRequest;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryOpView;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.MerchantApplicationStatusBrief;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.MerchantCatalog;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.SpuBrief;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.service.InventoryOperationService;

/**
 * product-service 内部接口，仅供 order-service / user-service 通过 Feign 调用。
 */
@RestController
@RequestMapping("/internal/products")
public class InternalProductController {

	private final InventoryOperationService inventoryOperationService;
	private final ProductSPUMapper productSPUMapper;
	private final MerchantMapper merchantMapper;

	public InternalProductController(InventoryOperationService inventoryOperationService,
			ProductSPUMapper productSPUMapper,
			MerchantMapper merchantMapper) {
		this.inventoryOperationService = inventoryOperationService;
		this.productSPUMapper = productSPUMapper;
		this.merchantMapper = merchantMapper;
	}

	@PostMapping("/checkout-reserve")
	public Result<List<CheckoutItemSnapshot>> checkoutReserve(@RequestBody CheckoutRequest request) {
		return Result.success(inventoryOperationService.reserve(request));
	}

	@GetMapping("/inventory/ops/{opId}")
	public Result<InventoryOpView> getInventoryOp(@PathVariable("opId") String opId,
			@RequestParam(value = "opType", defaultValue = "reserve") String opType) {
		return Result.success(inventoryOperationService.getOp(opId, opType));
	}

	@PostMapping("/inventory/release")
	public Result<Void> releaseInventory(@RequestBody InventoryReleaseRequest request) {
		inventoryOperationService.release(request);
		return Result.<Void>success("released", null);
	}

	@PostMapping("/spus/batch")
	public Result<List<SpuBrief>> spusBatch(@RequestBody List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Result.success(List.of());
		}
		List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) {
			return Result.success(List.of());
		}
		List<ProductSPU> spus = productSPUMapper.selectBatchIds(distinctIds);
		Map<Long, Merchant> merchantMap = loadMerchants(spus);
		List<SpuBrief> result = new ArrayList<>();
		for (ProductSPU spu : spus) {
			Merchant merchant = spu.getMerchantId() == null ? null : merchantMap.get(spu.getMerchantId());
			SpuBrief brief = new SpuBrief();
			brief.setId(spu.getId());
			brief.setName(spu.getName());
			brief.setImageUrl(spu.getImageUrl());
			brief.setMerchantId(spu.getMerchantId());
			brief.setMerchantName(merchant == null ? null : merchant.getMerchantName());
			brief.setMerchantUserId(merchant == null ? null : merchant.getUserId());
			brief.setIsActive(spu.getIsActive() != null && spu.getIsActive() == 1);
			brief.setStatus(spu.getStatus());
			result.add(brief);
		}
		return Result.success(result);
	}

	@GetMapping("/merchants/by-user/{userId}/catalog")
	public Result<MerchantCatalog> merchantCatalog(@PathVariable("userId") Long userId) {
		Long merchantId = merchantMapper.selectMerchantIdByUserId(userId);
		if (merchantId == null) {
			return Result.success(null);
		}
		List<ProductSPU> spus = productSPUMapper.selectList(
			new LambdaQueryWrapper<ProductSPU>()
				.eq(ProductSPU::getMerchantId, merchantId)
				.orderByDesc(ProductSPU::getId));
		List<CatalogSpu> catalogSpus = spus.stream().map(spu -> {
			CatalogSpu item = new CatalogSpu();
			item.setId(spu.getId());
			item.setName(spu.getName());
			item.setImageUrl(spu.getImageUrl());
			item.setIsActive(spu.getIsActive() != null && spu.getIsActive() == 1);
			item.setStatus(spu.getStatus());
			return item;
		}).toList();
		MerchantCatalog catalog = new MerchantCatalog();
		catalog.setMerchantId(merchantId);
		catalog.setSpus(catalogSpus);
		return Result.success(catalog);
	}

	@GetMapping("/merchants/by-user/{userId}/application-status")
	public Result<MerchantApplicationStatusBrief> applicationStatus(@PathVariable("userId") Long userId) {
		List<Merchant> merchants = merchantMapper.selectList(
			new LambdaQueryWrapper<Merchant>()
				.eq(Merchant::getUserId, userId)
				.orderByDesc(Merchant::getId)
				.last("LIMIT 1"));
		if (merchants.isEmpty()) {
			return Result.success(null);
		}
		Merchant merchant = merchants.get(0);
		MerchantApplicationStatusBrief brief = new MerchantApplicationStatusBrief();
		brief.setMerchantId(merchant.getId());
		brief.setStatus(merchant.getStatus());
		return Result.success(brief);
	}

	private Map<Long, Merchant> loadMerchants(List<ProductSPU> spus) {
		List<Long> merchantIds = spus.stream()
			.map(ProductSPU::getMerchantId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		if (merchantIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Merchant> map = new LinkedHashMap<>();
		for (Merchant merchant : merchantMapper.selectBatchIds(merchantIds)) {
			map.put(merchant.getId(), merchant);
		}
		return map;
	}
}
