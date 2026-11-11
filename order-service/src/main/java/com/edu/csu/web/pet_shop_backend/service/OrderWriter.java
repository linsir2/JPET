package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.csu.web.pet_shop_backend.entity.model.Order;
import com.edu.csu.web.pet_shop_backend.mapper.OrderMapper;

@Service
public class OrderWriter {

	private final OrderMapper orderMapper;

	public OrderWriter(OrderMapper orderMapper) {
		this.orderMapper = orderMapper;
	}

	@Transactional(rollbackFor = Exception.class)
	public void insertOrders(List<Order> orders) {
		for (Order order : orders) {
			orderMapper.insert(order);
		}
	}
}
