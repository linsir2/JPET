package com.edu.csu.web.pet_shop_backend.service;

import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantApplyDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantApplicationStatusVo;

/**
 * 商家入驻申请服务接口。
 */
public interface MerchantApplicationService {

	/**
	 * 提交商家入驻申请。
	 * @param applyDto 商家入驻申请参数
	 * @param userId 当前登录用户 ID
	 * @return 商家记录 ID
	 */
	Long applyMerchant(MerchantApplyDto applyDto, Long userId);

	/**
	 * 查询当前登录用户最近一次商家入驻申请状态。
	 * @param userId 当前登录用户 ID
	 * @return 最近一次申请状态；若从未申请则返回 null
	 */
	MerchantApplicationStatusVo getCurrentApplicationStatus(Long userId);

	void withdrawApplication(Long userId);
}