package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;

import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerProductVo;

public interface BuyerProductService {

	List<BuyerProductVo> listProducts(String category, String keyword);

	BuyerProductVo getProductDetail(Long productId);

	List<String> listCategories();

	List<String> listSuggestions(String keyword);
}