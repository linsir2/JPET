package com.edu.csu.web.pet_shop_backend.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.dto.UserProfileUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserProfileVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.service.AccountDeletionService;
import com.edu.csu.web.pet_shop_backend.service.UserProfileService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/user/profile")
public class UserProfileController {

	private final UserProfileService userProfileService;
	private final AccountDeletionService accountDeletionService;

	public UserProfileController(UserProfileService userProfileService,
			AccountDeletionService accountDeletionService) {
		this.userProfileService = userProfileService;
		this.accountDeletionService = accountDeletionService;
	}

	@GetMapping
	public Result<UserProfileVo> getProfile(@InjectJWTUserContext JWTUserContext jwtUserContext) {
		return Result.success(userProfileService.getProfileForCurrentUser(resolveUserId(jwtUserContext)));
	}

	@PutMapping
	public Result<UserProfileVo> updateProfile(@Valid @RequestBody UserProfileUpdateDto request,
			@InjectJWTUserContext JWTUserContext jwtUserContext) {
		return Result.success(userProfileService.updateProfileForCurrentUser(request, resolveUserId(jwtUserContext)));
	}

	@DeleteMapping
	public Result<Void> deleteAccount(@InjectJWTUserContext JWTUserContext jwtUserContext) {
		accountDeletionService.deleteAccount(resolveUserId(jwtUserContext));
		return Result.<Void>success("deleted", null);
	}

	private Long resolveUserId(JWTUserContext jwtUserContext) {
		if (jwtUserContext == null || jwtUserContext.getUserId() == null) {
			throw BusinessException.unauthorized("未登录或登录已失效");
		}
		return jwtUserContext.getUserId();
	}
}