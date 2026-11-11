package com.edu.csu.web.pet_shop_backend.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantApplyDto;
import com.edu.csu.web.pet_shop_backend.entity.model.Merchant;
import com.edu.csu.web.pet_shop_backend.entity.model.MerchantTagRel;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantApplicationStatusVo;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.exception.AccountStatusInvalidException;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.exception.LoginException;
import com.edu.csu.web.pet_shop_backend.exception.MerchantApplyConflictException;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantAuthenticationMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.client.UserDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.UserPatchDto;
import com.edu.csu.web.pet_shop_backend.service.MerchantApplicationService;
import com.edu.csu.web.pet_shop_backend.service.TagService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 商家入驻申请服务实现。
 */
@Service
public class MerchantApplicationServiceImpl implements MerchantApplicationService {

	private static final String USER_STATUS_NORMAL = "normal";
	private static final String MERCHANT_STATUS_PENDING = "pending";
	private static final String MERCHANT_STATUS_ACTIVE = "active";
	private static final String MERCHANT_STATUS_REJECTED = "rejected";
	private static final String MERCHANT_STATUS_BANNED = "banned";

	private final UserDirectory userDirectory;
	private final MerchantMapper merchantMapper;
	private final MerchantTagRelMapper merchantTagRelMapper;
	private final MerchantAuthenticationMapper merchantAuthenticationMapper;
	private final ProductSPUMapper productSPUMapper;
	private final TagService tagService;
	private final ObjectMapper objectMapper;

	public MerchantApplicationServiceImpl(UserDirectory userDirectory, MerchantMapper merchantMapper,
			MerchantTagRelMapper merchantTagRelMapper, MerchantAuthenticationMapper merchantAuthenticationMapper,
			ProductSPUMapper productSPUMapper, TagService tagService, ObjectMapper objectMapper) {
		this.userDirectory = userDirectory;
		this.merchantMapper = merchantMapper;
		this.merchantTagRelMapper = merchantTagRelMapper;
		this.merchantAuthenticationMapper = merchantAuthenticationMapper;
		this.productSPUMapper = productSPUMapper;
		this.tagService = tagService;
		this.objectMapper = objectMapper;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Long applyMerchant(MerchantApplyDto applyDto, Long userId) {
		if (userId == null) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}

		UserBriefVo user = userDirectory.get(userId);
		if (user == null) {
			throw new LoginException("登录信息已失效，请重新登录", userId);
		}
		if (!USER_STATUS_NORMAL.equals(user.getStatus())) {
			throw new AccountStatusInvalidException("user", userId, user.getStatus(), "当前账号状态异常，无法提交商家申请");
		}
		if (UserRoleEnum.MERCHANT_USER.getValue().equals(user.getRole())) {
			throw new MerchantApplyConflictException(userId, MERCHANT_STATUS_ACTIVE, "当前账号已经是商家用户，无需重复申请");
		}

		String merchantName = normalizeRequired(applyDto.getMerchantName());
		String email = normalizeEmail(applyDto.getEmail());
		String phone = normalizeRequired(applyDto.getPhone());
		String imageUrl = normalizeRequired(applyDto.getImageUrl());
		String description = normalizeNullable(applyDto.getDescription());
		List<String> normalizedTags = normalizeTags(applyDto.getTags());

		Merchant existingMerchant = getLatestMerchantByUserId(userId);
		validateMerchantNameUnique(merchantName, existingMerchant == null ? null : existingMerchant.getId());
		if (existingMerchant != null) {
			handleMerchantStatus(existingMerchant, userId);
		}
		// 本地校验通过后再同步用户联系方式，避免校验失败却改了用户资料
		userDirectory.patch(userId, UserPatchDto.phone(email, phone));

		Long merchantId;
		if (existingMerchant == null) {
			Merchant merchant = new Merchant();
			merchant.setUserId(userId);
			merchant.setMerchantName(merchantName);
			merchant.setImageUrl(imageUrl);
			merchant.setDescription(description);
			merchant.setStatus(MERCHANT_STATUS_PENDING);
			merchant.setAuditReason(null);
			merchant.setTurnover(BigDecimal.ZERO);
			merchantMapper.insert(merchant);
			merchantId = merchant.getId();
		} else {
			existingMerchant.setMerchantName(merchantName);
			existingMerchant.setImageUrl(imageUrl);
			existingMerchant.setDescription(description);
			existingMerchant.setStatus(MERCHANT_STATUS_PENDING);
			existingMerchant.setAuditReason(null);
			merchantMapper.updateById(existingMerchant);
			merchantId = existingMerchant.getId();
		}

		replaceMerchantTags(merchantId, normalizedTags);
		return merchantId;
	}

	@Override
	public MerchantApplicationStatusVo getCurrentApplicationStatus(Long userId) {
		if (userId == null) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}

		Merchant merchant = getLatestMerchantByUserId(userId);
		if (merchant == null) {
			return null;
		}

		MerchantApplicationStatusVo vo = new MerchantApplicationStatusVo();
		vo.setMerchantId(merchant.getId());
		vo.setMerchantName(merchant.getMerchantName());
		UserBriefVo user = userDirectory.get(userId);
		vo.setEmail(user == null ? null : user.getEmail());
		vo.setPhone(readPhone(user == null ? null : user.getProfile()));
		vo.setImageUrl(merchant.getImageUrl());
		vo.setStatus(merchant.getStatus());
		vo.setAuditReason(merchant.getAuditReason());
		vo.setCreatedAt(merchant.getCreateTime());
		return vo;
	}


	@Override
	@Transactional(rollbackFor = Exception.class)
	public void withdrawApplication(Long userId) {
		if (userId == null) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
		Merchant merchant = getLatestMerchantByUserId(userId);
		if (merchant == null) {
			throw BusinessException.notFound("商家入驻申请不存在");
		}
		String status = merchant.getStatus();
		if (!MERCHANT_STATUS_PENDING.equals(status) && !MERCHANT_STATUS_REJECTED.equals(status)) {
			throw BusinessException.conflict("只有待审核或已拒绝的申请可以撤回");
		}
		Long authCount = merchantAuthenticationMapper.selectCount(
			new LambdaQueryWrapper<com.edu.csu.web.pet_shop_backend.entity.model.MerchantAuthentication>()
				.eq(com.edu.csu.web.pet_shop_backend.entity.model.MerchantAuthentication::getMerchantId, merchant.getId()));
		if (authCount != null && authCount > 0) {
			throw BusinessException.conflict("该申请存在关联认证，无法撤回");
		}
		Long spuCount = productSPUMapper.selectCount(
			new LambdaQueryWrapper<ProductSPU>().eq(ProductSPU::getMerchantId, merchant.getId()));
		if (spuCount != null && spuCount > 0) {
			throw BusinessException.conflict("该申请存在关联商品，无法撤回");
		}
		merchantTagRelMapper.delete(new LambdaQueryWrapper<MerchantTagRel>()
			.eq(MerchantTagRel::getMerchantId, merchant.getId()));
		merchantMapper.deleteById(merchant.getId());
	}

	private Merchant getLatestMerchantByUserId(Long userId) {
		List<Merchant> merchants = merchantMapper.selectList(
			new LambdaQueryWrapper<Merchant>()
				.eq(Merchant::getUserId, userId)
				.orderByDesc(Merchant::getId)
		);
		return merchants.isEmpty() ? null : merchants.get(0);
	}

	private void handleMerchantStatus(Merchant merchant, Long userId) {
		String status = merchant.getStatus();
		if (MERCHANT_STATUS_PENDING.equals(status)) {
			throw new MerchantApplyConflictException(userId, status, "当前账号已经提交过商家申请，请等待管理员审核");
		}
		if (MERCHANT_STATUS_ACTIVE.equals(status)) {
			throw new MerchantApplyConflictException(userId, status, "当前账号已经完成商家入驻，无需重复申请");
		}
		if (MERCHANT_STATUS_BANNED.equals(status)) {
			throw new AccountStatusInvalidException("merchant", merchant.getId(), status, "当前商家账号已被封禁，无法重新申请");
		}
		if (!MERCHANT_STATUS_REJECTED.equals(status)) {
			throw new MerchantApplyConflictException(userId, status, "当前商家申请状态异常，无法重新提交申请");
		}
	}

	private void validateMerchantNameUnique(String merchantName, Long excludeMerchantId) {
		LambdaQueryWrapper<Merchant> queryWrapper = new LambdaQueryWrapper<Merchant>()
			.eq(Merchant::getMerchantName, merchantName);
		if (excludeMerchantId != null) {
			queryWrapper.ne(Merchant::getId, excludeMerchantId);
		}

		Long count = merchantMapper.selectCount(queryWrapper);
		if (count != null && count > 0) {
			throw new MerchantApplyConflictException(null, null, "商家名称已存在，请更换后重试");
		}
	}

	private void replaceMerchantTags(Long merchantId, List<String> tagNames) {
		List<MerchantTagRel> existingRelations = merchantTagRelMapper.selectList(
			new LambdaQueryWrapper<MerchantTagRel>().eq(MerchantTagRel::getMerchantId, merchantId)
		);

		if (tagNames == null || tagNames.isEmpty()) {
			for (MerchantTagRel relation : existingRelations) {
				merchantTagRelMapper.deleteById(relation.getId());
			}
			return;
		}

		Map<String, Long> tagIdMap = tagService.ensureTagsExist(tagNames);
		Set<Long> desiredTagIds = new LinkedHashSet<>(tagIdMap.values());

		Map<Long, Long> existingRelationIdByTagId = new LinkedHashMap<>();
		for (MerchantTagRel relation : existingRelations) {
			existingRelationIdByTagId.put(relation.getTagId(), relation.getId());
		}

		for (Long tagId : desiredTagIds) {
			if (!existingRelationIdByTagId.containsKey(tagId)) {
				MerchantTagRel relation = new MerchantTagRel();
				relation.setMerchantId(merchantId);
				relation.setTagId(tagId);
				merchantTagRelMapper.insert(relation);
			}
		}

		for (Map.Entry<Long, Long> entry : existingRelationIdByTagId.entrySet()) {
			if (!desiredTagIds.contains(entry.getKey())) {
				merchantTagRelMapper.deleteById(entry.getValue());
			}
		}
	}

	private String normalizeRequired(String value) {
		return value == null ? null : value.trim();
	}

	private String normalizeEmail(String value) {
		String trimmed = normalizeRequired(value);
		return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private List<String> normalizeTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return List.of();
		}

		return new ArrayList<>(tags.stream()
			.map(this::normalizeNullable)
			.filter(tag -> tag != null && !tag.isEmpty())
			.distinct()
			.toList());
	}

	private String readPhone(String rawProfile) {
		return readProfile(rawProfile).getPhone();
	}

	private BuyerProfileExtra readProfile(String rawProfile) {
		if (rawProfile == null || rawProfile.isBlank()) {
			return new BuyerProfileExtra();
		}
		try {
			BuyerProfileExtra extra = objectMapper.readValue(rawProfile, BuyerProfileExtra.class);
			return extra == null ? new BuyerProfileExtra() : extra;
		} catch (Exception ignored) {
			return new BuyerProfileExtra();
		}
	}

	private static class BuyerProfileExtra {
		private String phone;

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}
	}
}