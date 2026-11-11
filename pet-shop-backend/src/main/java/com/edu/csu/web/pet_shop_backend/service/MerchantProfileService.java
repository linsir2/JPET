package com.edu.csu.web.pet_shop_backend.service;

import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantProfileUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantProfileVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantStatsVo;

/**
 * 商家资料展示与修改服务接口。
 */
public interface MerchantProfileService {

	/**
	 * 查询当前登录商家的资料。
	 * @param userId 当前登录用户 ID
	 * @param username 当前登录用户名
	 * @return 商家资料展示对象
	 */
	MerchantProfileVo getProfileForCurrentUser(Long userId, String username);

	/**
	 * 修改当前登录商家的资料。
	 * @param request 修改请求
	 * @param userId 当前登录用户 ID
	 * @param username 当前登录用户名
	 * @return 修改后的商家资料展示对象
	 */
	MerchantProfileVo updateProfileForCurrentUser(MerchantProfileUpdateDto request, Long userId, String username);

	/**
	 * 更新当前商家的头像图片。
	 */
	MerchantProfileVo updateAvatarForCurrentUser(String imageUrl, Long userId, String username);

	MerchantStatsVo getStatsForCurrentUser(Long userId);
}
