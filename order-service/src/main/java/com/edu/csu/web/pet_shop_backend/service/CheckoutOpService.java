package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.edu.csu.web.pet_shop_backend.entity.model.CheckoutOp;
import com.edu.csu.web.pet_shop_backend.mapper.CheckoutOpMapper;

@Service
public class CheckoutOpService {

	private final CheckoutOpMapper checkoutOpMapper;

	public CheckoutOpService(CheckoutOpMapper checkoutOpMapper) {
		this.checkoutOpMapper = checkoutOpMapper;
	}

	public CheckoutOp createReserving(String opId, Long userId, String itemsJson) {
		CheckoutOp operation = new CheckoutOp();
		operation.setOpId(opId);
		operation.setUserId(userId);
		operation.setStatus("RESERVING");
		operation.setItemsJson(itemsJson);
		checkoutOpMapper.insert(operation);
		return operation;
	}

	public CheckoutOp get(String opId) {
		return checkoutOpMapper.selectOne(opId);
	}

	public void mark(String opId, String status, String reserveResultJson, String lastError) {
		CheckoutOp operation = new CheckoutOp();
		operation.setOpId(opId);
		operation.setStatus(status);
		operation.setReserveResultJson(reserveResultJson);
		operation.setLastError(lastError);
		checkoutOpMapper.update(operation);
	}

	public List<CheckoutOp> findStale(int minutes) {
		return checkoutOpMapper.selectStale(minutes, 50);
	}
}
