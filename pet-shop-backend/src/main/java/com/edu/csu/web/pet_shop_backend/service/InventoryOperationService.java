package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;

import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutItemSnapshot;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.CheckoutRequest;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryOpView;
import com.edu.csu.web.pet_shop_backend.internal.InternalProductContracts.InventoryReleaseRequest;

public interface InventoryOperationService {

	List<CheckoutItemSnapshot> reserve(CheckoutRequest request);

	InventoryOpView getOp(String opId, String opType);

	void release(InventoryReleaseRequest request);
}
