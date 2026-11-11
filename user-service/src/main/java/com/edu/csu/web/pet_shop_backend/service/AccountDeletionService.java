package com.edu.csu.web.pet_shop_backend.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.edu.csu.web.pet_shop_backend.client.ProductClient;
import com.edu.csu.web.pet_shop_backend.client.dto.MerchantApplicationStatusBrief;
import com.edu.csu.web.pet_shop_backend.common.utils.PasswordUtil;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.mapper.UserMapper;

@Service
public class AccountDeletionService {

	private final UserMapper userMapper;
	private final PasswordUtil passwordUtil;
	private final ProductClient productClient;

	public AccountDeletionService(UserMapper userMapper, PasswordUtil passwordUtil, ProductClient productClient) {
		this.userMapper = userMapper;
		this.passwordUtil = passwordUtil;
		this.productClient = productClient;
	}

	@Transactional(rollbackFor = Exception.class)
	public void deleteAccount(Long userId) {
		if (userId == null || userId <= 0) {
			throw BusinessException.unauthorized("未登录或登录已失效");
		}
		User user = userMapper.selectById(userId);
		if (user == null) {
			throw BusinessException.notFound("用户不存在");
		}
		if ("deleted".equals(user.getStatus())) {
			return;
		}
		if (!UserRoleEnum.NORMAL_USER.getValue().equals(user.getRole())) {
			throw BusinessException.conflict("商家账号需先完成商家注销，当前暂不支持自助注销");
		}

		ensureNoOpenMerchantApplication(userId);

		String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
		user.setUsername("deleted_user_" + userId + "_" + suffix);
		user.setEmail("deleted_user_" + userId + "_" + suffix + "@deleted.invalid");
		user.setPassword(passwordUtil.encode(UUID.randomUUID().toString()));
		user.setStatus("deleted");
		user.setProfile(null);
		userMapper.update(user, new LambdaUpdateWrapper<User>()
			.eq(User::getId, userId)
			.set(User::getProfile, null));
	}

	private void ensureNoOpenMerchantApplication(Long userId) {
		Result<MerchantApplicationStatusBrief> result;
		try {
			result = productClient.applicationStatus(userId);
		} catch (Exception ex) {
			throw BusinessException.conflict("无法确认商家申请状态，请稍后重试");
		}
		if (result == null) {
			throw BusinessException.conflict("无法确认商家申请状态，请稍后重试");
		}
		if (result.getCode() >= 400) {
			throw new BusinessException(result.getCode(), result.getMessage());
		}
		MerchantApplicationStatusBrief brief = result.getData();
		if (brief == null || brief.getStatus() == null) {
			return;
		}
		String status = brief.getStatus().toLowerCase(Locale.ROOT);
		if ("pending".equals(status) || "rejected".equals(status)) {
			throw BusinessException.conflict("请先撤回商家入驻申请后再注销账号");
		}
		if ("active".equals(status) || "banned".equals(status)) {
			throw BusinessException.conflict("商家账号需先完成商家注销，当前暂不支持自助注销");
		}
	}
}
