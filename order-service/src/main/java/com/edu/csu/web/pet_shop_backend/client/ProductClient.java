package com.edu.csu.web.pet_shop_backend.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.CheckoutRequest;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderClientDtos.InventoryReleaseRequest;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.CheckoutItemSnapshot;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.InventoryOpView;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.MerchantCatalog;
import com.edu.csu.web.pet_shop_backend.client.vo.OrderClientVos.SpuBrief;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;

@FeignClient(name = "product-service", path = "/internal/products")
public interface ProductClient {

	@PostMapping("/checkout-reserve")
	Result<List<CheckoutItemSnapshot>> reserve(@RequestBody CheckoutRequest request);

	@GetMapping("/inventory/ops/{opId}")
	Result<InventoryOpView> getInventoryOp(@PathVariable("opId") String opId,
		@RequestParam("opType") String opType);

	@PostMapping("/inventory/release")
	Result<Void> release(@RequestBody InventoryReleaseRequest request);

	@PostMapping("/spus/batch")
	Result<List<SpuBrief>> spusBatch(@RequestBody List<Long> ids);

	@GetMapping("/merchants/by-user/{userId}/catalog")
	Result<MerchantCatalog> merchantCatalog(@PathVariable("userId") Long userId);
}
