package com.edu.csu.web.pet_shop_backend.service;

import java.util.Map;

import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerOrderAddressDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerCheckoutDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.BuyerOrderReviewDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerCheckoutVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerOrderVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantOrderVo;

public interface OrderService {
	BuyerCheckoutVo checkout(BuyerCheckoutDto dto, Long userId);

	Map<String, Object> listBuyerOrders(Long userId, String status);

	BuyerOrderVo cancelOrder(Long orderId, Long userId);

	BuyerOrderVo confirmReceived(Long orderId, Long userId);

	BuyerOrderVo updateOrderAddress(Long orderId, BuyerOrderAddressDto dto, Long userId);

	BuyerOrderVo requestReturn(Long orderId, Long userId);

	BuyerOrderVo reviewOrder(Long orderId, BuyerOrderReviewDto dto, Long userId, String username);

	Map<String, Object> listMerchantOrders(Long merchantUserId, String status, boolean history);

	MerchantOrderVo markOrderShipped(Long orderId, Long merchantUserId);

	void deleteOrder(Long orderId, Long userId);

	void deleteReview(Long orderId, Long userId);
}