package com.edu.csu.web.pet_shop_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerProductVo;
import com.edu.csu.web.pet_shop_backend.service.BuyerProductService;

@RestController
@RequestMapping("/api/products")
public class BuyerProductController {

	private final BuyerProductService buyerProductService;

	public BuyerProductController(BuyerProductService buyerProductService) {
		this.buyerProductService = buyerProductService;
	}

	@GetMapping
	public Result<List<BuyerProductVo>> listProducts(
			@RequestParam(value = "category", required = false) String category,
			@RequestParam(value = "search", required = false) String search) {
		return Result.success(buyerProductService.listProducts(category, search));
	}

	@GetMapping("/{id}")
	public Result<BuyerProductVo> getProduct(@PathVariable Long id) {
		return Result.success(buyerProductService.getProductDetail(id));
	}

	@GetMapping("/categories")
	public Result<List<String>> listCategories() {
		return Result.success(buyerProductService.listCategories());
	}

	@GetMapping("/suggestions")
	public Result<List<String>> listSuggestions(@RequestParam("keyword") String keyword) {
		return Result.success(buyerProductService.listSuggestions(keyword));
	}
}