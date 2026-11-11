package com.edu.csu.web.pet_shop_backend.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminLoginDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminRegisterDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantLoginDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.TokenRefreshDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.UserRegisterDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.LoginVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.TokenRefreshVo;
import com.edu.csu.web.pet_shop_backend.service.AuthService;

import jakarta.validation.Valid;

/**
 * 认证控制器，负责登录、注册与 token 刷新。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	/**
	 * 买家注册接口。
	 * 仅创建普通用户账号，不自动提交商家入驻审核。
	 * @param registerDto 注册参数
	 * @return 统一响应结果
	 */
	@PostMapping("/user/register")
	public Result<Void> registerUser(@Valid @RequestBody UserRegisterDto registerDto) {
		authService.registerUser(registerDto);
		return Result.<Void>success("买家注册成功，请登录", null);
	}

	@PostMapping("/admin/register")
	public Result<Void> registerAdmin(@Valid @RequestBody AdminRegisterDto registerDto) {
		authService.registerAdmin(registerDto);
		return Result.<Void>success("管理员注册成功，请登录", null);
	}

	/**
	 * 商家登录接口。
	 * 普通用户也允许从该入口登录，登录后由前端引导其继续进行商家入驻申请。
	 * @param loginDto 登录参数
	 * @return 登录结果
	 */
	@PostMapping("/merchant/login")
	public Result<LoginVo> loginMerchant(@Valid @RequestBody MerchantLoginDto loginDto) {
		return Result.success(authService.loginMerchant(loginDto));
	}

	/**
	 * 管理员登录接口。
	 * @param loginDto 登录参数
	 * @return 登录结果
	 */
	@PostMapping("/admin/login")
	public Result<LoginVo> loginAdmin(@Valid @RequestBody AdminLoginDto loginDto) {
		return Result.success(authService.loginAdmin(loginDto));
	}

	/**
	 * 刷新 access_token 接口。
	 * @param refreshDto refresh_token 参数
	 * @return 新 access_token
	 */
	@PostMapping("/token/refresh")
	public Result<TokenRefreshVo> refreshToken(@Valid @RequestBody TokenRefreshDto refreshDto) {
		return Result.success(authService.refreshAccessToken(refreshDto));
	}
}