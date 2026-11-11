package com.edu.csu.web.pet_shop_backend.service;

import com.edu.csu.web.pet_shop_backend.entity.dto.AdminLoginDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminRegisterDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantLoginDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.TokenRefreshDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.UserRegisterDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.LoginVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.TokenRefreshVo;

/**
 * 认证服务接口，负责登录、注册与 token 刷新。
 */
public interface AuthService {

	/**
	 * 买家用户注册接口。
	 * 仅注册普通用户账号，不创建商家入驻记录。
	 * @param registerDto 注册参数
	 */
	void registerUser(UserRegisterDto registerDto);

	/**
	 * 管理员注册接口。
	 * 通过不同注册密钥分别注册主管理员与子管理员。
	 * @param registerDto 注册参数
	 */
	void registerAdmin(AdminRegisterDto registerDto);

	/**
	 * 商家登录接口。
	 * 允许普通用户和商家用户使用该入口登录，以便普通用户登录后继续完成商家入驻流程。
	 * @param loginDto 登录参数
	 * @return 登录结果，包含 access_token 与 refresh_token
	 */
	LoginVo loginMerchant(MerchantLoginDto loginDto);

	/**
	 * 管理员登录接口。
	 * @param loginDto 登录参数
	 * @return 登录结果，包含 access_token 与 refresh_token
	 */
	LoginVo loginAdmin(AdminLoginDto loginDto);

	/**
	 * 刷新 access_token。
	 * @param refreshDto refresh_token 参数
	 * @return 新的 access_token
	 */
	TokenRefreshVo refreshAccessToken(TokenRefreshDto refreshDto);
}