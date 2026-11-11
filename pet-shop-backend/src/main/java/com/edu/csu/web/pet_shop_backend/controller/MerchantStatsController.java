package com.edu.csu.web.pet_shop_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantStatsVo;
import com.edu.csu.web.pet_shop_backend.service.MerchantProfileService;

@RestController
@RequestMapping("/api/merchant/stats")
public class MerchantStatsController {

	private final MerchantProfileService merchantProfileService;

	public MerchantStatsController(MerchantProfileService merchantProfileService) {
		this.merchantProfileService = merchantProfileService;
	}

	@GetMapping
	public Result<MerchantStatsVo> getStats(@InjectJWTUserContext JWTUserContext jwtUserContext) {
		return Result.success(merchantProfileService.getStatsForCurrentUser(
			jwtUserContext == null ? null : jwtUserContext.getUserId()
		));
	}
}