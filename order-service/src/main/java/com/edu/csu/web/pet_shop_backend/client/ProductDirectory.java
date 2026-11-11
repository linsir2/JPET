package com.edu.csu.web.pet_shop_backend.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.CheckoutRequest;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.CheckoutItemSnapshot;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.InventoryOpView;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.MerchantCatalog;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.SpuBrief;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;

@Component
public class ProductDirectory {

	private final ProductClient productClient;

	public ProductDirectory(ProductClient productClient) {
		this.productClient = productClient;
	}

	public List<CheckoutItemSnapshot> reserve(CheckoutRequest request) {
		Result<List<CheckoutItemSnapshot>> result = productClient.reserve(request);
		ensureSuccess(result);
		return result == null || result.getData() == null ? List.of() : result.getData();
	}

	public InventoryOpView getOp(String opId, String opType) {
		Result<InventoryOpView> result = productClient.getInventoryOp(opId, opType);
		ensureSuccess(result);
		return result == null ? null : result.getData();
	}

	public void release(InventoryReleaseRequest request) {
		Result<Void> result = productClient.release(request);
		ensureSuccess(result);
	}

	public Map<Long, SpuBrief> spusBatch(Collection<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return Map.of();
		}
		List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) {
			return Map.of();
		}
		Result<List<SpuBrief>> result = productClient.spusBatch(distinctIds);
		ensureSuccess(result);
		Map<Long, SpuBrief> map = new LinkedHashMap<>();
		if (result != null && result.getData() != null) {
			for (SpuBrief item : result.getData()) {
				if (item != null && item.getId() != null) {
					map.put(item.getId(), item);
				}
			}
		}
		return map;
	}

	public MerchantCatalog catalog(Long userId) {
		Result<MerchantCatalog> result = productClient.merchantCatalog(userId);
		ensureSuccess(result);
		return result == null ? null : result.getData();
	}

	private void ensureSuccess(Result<?> result) {
		if (result == null) {
			throw BusinessException.badRequest("product-service 返回为空");
		}
		if (result.getCode() >= 400) {
			throw new BusinessException(result.getCode(), result.getMessage());
		}
	}
}
