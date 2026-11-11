package com.edu.csu.web.pet_shop_backend.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantProfileUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.model.Merchant;
import com.edu.csu.web.pet_shop_backend.entity.model.MerchantTagRel;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantProfileVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantStatsVo;
import com.edu.csu.web.pet_shop_backend.exception.AccountStatusInvalidException;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.exception.LoginException;
import com.edu.csu.web.pet_shop_backend.exception.MerchantNotFoundException;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.client.UserDirectory;
import com.edu.csu.web.pet_shop_backend.client.OrderDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.MerchantAggregateResponse;
import com.edu.csu.web.pet_shop_backend.client.dto.UserPatchDto;
import com.edu.csu.web.pet_shop_backend.service.MerchantProfileService;
import com.edu.csu.web.pet_shop_backend.service.TagService;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 商家资料展示与修改服务实现。
 */
@Service
public class MerchantProfileServiceImpl implements MerchantProfileService {

	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

	private static final String MERCHANT_STATUS_ACTIVE = "active";

	private static final int MAX_TAG_COUNT = 30;

	private static final int MAX_TAG_NAME_LENGTH = 50;

	private static final int MAX_IMAGE_URL_LENGTH = 500;

	private static final int MAX_MERCHANT_NAME_LENGTH = 100;

	private static final Pattern LEGACY_REGISTERED_MERCHANT_NAME = Pattern.compile("(.+)_merchant_\\d+$");

	private final MerchantMapper merchantMapper;

	private final MerchantTagRelMapper merchantTagRelMapper;

	private final ProductSPUMapper productSPUMapper;

	private final OrderDirectory orderDirectory;

	private final UserDirectory userDirectory;

	private final TagService tagService;

	public MerchantProfileServiceImpl(MerchantMapper merchantMapper, MerchantTagRelMapper merchantTagRelMapper,
			ProductSPUMapper productSPUMapper, OrderDirectory orderDirectory,
			UserDirectory userDirectory, TagService tagService) {
		this.merchantMapper = merchantMapper;
		this.merchantTagRelMapper = merchantTagRelMapper;
		this.productSPUMapper = productSPUMapper;
		this.orderDirectory = orderDirectory;
		this.userDirectory = userDirectory;
		this.tagService = tagService;
	}

	@Override
	public MerchantProfileVo getProfileForCurrentUser(Long userId, String username) {
		Merchant merchant = loadAccessibleMerchant(userId);
		return buildVo(merchant, username);
	}

	@Override
	public MerchantStatsVo getStatsForCurrentUser(Long userId) {
		Merchant merchant = loadAccessibleMerchant(userId);
		LocalDate now = LocalDate.now();
		LocalDateTime currentMonthStart = now.withDayOfMonth(1).atStartOfDay();
		LocalDateTime nextMonthStart = now.plusMonths(1).withDayOfMonth(1).atStartOfDay();
		LocalDateTime previousMonthStart = now.minusMonths(1).withDayOfMonth(1).atStartOfDay();

		List<Long> spuIds = activeSpuIds(merchant.getId());
		MerchantAggregateResponse aggregate = orderDirectory.merchantAggregates(
			spuIds,
			currentMonthStart.toString(),
			nextMonthStart.toString(),
			previousMonthStart.toString());
		BigDecimal currentRevenue = safeAmount(aggregate.getCurrentRevenue());
		BigDecimal previousRevenue = safeAmount(aggregate.getPreviousRevenue());
		BigDecimal totalRevenue = safeAmount(aggregate.getTotalRevenue());

		MerchantStatsVo vo = new MerchantStatsVo();
		vo.setMonthRevenue(currentRevenue.setScale(2, RoundingMode.HALF_UP));
		vo.setTotalRevenue(totalRevenue.setScale(2, RoundingMode.HALF_UP));
		vo.setTrendPercent(calculateTrendPercent(currentRevenue, previousRevenue));
		vo.setRating(aggregate.getAverageRating() == null
			? BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP)
			: aggregate.getAverageRating().setScale(1, RoundingMode.HALF_UP));
		return vo;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public MerchantProfileVo updateProfileForCurrentUser(MerchantProfileUpdateDto request, Long userId,
			String username) {
		Merchant merchant = loadAccessibleMerchant(userId);

		if (request.getImageUrl() != null) {
			String imageUrl = normalizeRequired(request.getImageUrl(), "商家图片不能为空");
			if (imageUrl.length() > MAX_IMAGE_URL_LENGTH) {
				throw BusinessException.badRequest("商家图片 URL 长度不能超过 " + MAX_IMAGE_URL_LENGTH);
			}
			merchant.setImageUrl(imageUrl);
		}

		if (request.getMerchantName() != null) {
			String merchantName = normalizeRequired(request.getMerchantName(), "商家名称不能为空");
			if (merchantName.codePointCount(0, merchantName.length()) > MAX_MERCHANT_NAME_LENGTH) {
				throw BusinessException.badRequest("商家名称长度不能超过 " + MAX_MERCHANT_NAME_LENGTH + " 个字符");
			}
			if (!merchantName.equals(merchant.getMerchantName())) {
				assertMerchantNameChangeAllowed(merchant);
				assertMerchantNameUnique(merchantName, merchant.getId());
				merchant.setMerchantName(merchantName);
				merchant.setLastMerchantNameChangeTime(LocalDateTime.now());
			}
		}

		if (request.getEmail() != null) {
			String email = normalizeEmail(request.getEmail());
			userDirectory.patch(userId, UserPatchDto.email(email));
		}

		if (request.getDescription() != null) {
			String description = normalizeNullable(request.getDescription());
			assertPlainTextDescription(description);
			merchant.setDescription(description);
		}

		if (request.getTags() != null) {
			syncMerchantTags(merchant.getId(), request.getTags());
		}

		merchantMapper.updateById(merchant);
		Merchant refreshed = merchantMapper.selectById(merchant.getId());
		return buildVo(refreshed, username);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public MerchantProfileVo updateAvatarForCurrentUser(String imageUrl, Long userId, String username) {
		Merchant merchant = loadAccessibleMerchant(userId);
		String normalizedImageUrl = normalizeRequired(imageUrl, "头像图片不能为空");
		if (normalizedImageUrl.length() > MAX_IMAGE_URL_LENGTH) {
			throw BusinessException.badRequest("头像图片 URL 长度不能超过 " + MAX_IMAGE_URL_LENGTH);
		}
		merchant.setImageUrl(normalizedImageUrl);
		merchantMapper.updateById(merchant);
		Merchant refreshed = merchantMapper.selectById(merchant.getId());
		return buildVo(refreshed, username);
	}

	private Merchant loadAccessibleMerchant(Long userId) {
		if (userId == null) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}

		Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
			.eq(Merchant::getUserId, userId)
			.orderByDesc(Merchant::getId)
			.last("LIMIT 1"));
		if (merchant == null) {
			throw new MerchantNotFoundException(userId);
		}
		if (!MERCHANT_STATUS_ACTIVE.equals(merchant.getStatus())) {
			throw new AccountStatusInvalidException("merchant", merchant.getId(), merchant.getStatus(), "当前商家状态异常，无法访问商家资料");
		}
		return merchant;
	}

	private MerchantProfileVo buildVo(Merchant merchant, String username) {
		MerchantProfileVo vo = new MerchantProfileVo();
		UserBriefVo user = merchant.getUserId() == null ? null : userDirectory.get(merchant.getUserId());
		vo.setImageUrl(merchant.getImageUrl());
		vo.setMerchantName(resolveDisplayMerchantName(merchant.getMerchantName(), username));
		vo.setUsername(username);
		vo.setEmail(user == null ? null : user.getEmail());
		LocalDateTime created = merchant.getCreateTime();
		if (created != null) {
			LocalDate registrationDate = created.toLocalDate();
			long registeredDays = Math.max(0, ChronoUnit.DAYS.between(registrationDate, LocalDate.now()));
			vo.setRegistrationDate(registrationDate.format(DATE_FMT));
			vo.setRegistrationDurationDays(registeredDays);
			vo.setRegistrationDurationText("已营业 " + registeredDays + " 天");
		}
		else {
			vo.setRegistrationDurationDays(0);
			vo.setRegistrationDurationText("已营业 0 天");
		}
		vo.setTurnoverFromCompletedOrders(totalRevenue(merchant.getId()));
		vo.setDescription(merchant.getDescription());
		vo.setAuthentications(merchantMapper.selectAuthenticationNamesByMerchantId(merchant.getId()));
		vo.setTags(merchantMapper.selectTagNamesByMerchantId(merchant.getId()));
		return vo;
	}

	private String resolveDisplayMerchantName(String merchantName, String username) {
		String normalizedMerchantName = normalizeNullable(merchantName);
		String normalizedUsername = normalizeNullable(username);
		if (normalizedMerchantName != null) {
			String cleanedMerchantName = cleanupLegacyMerchantName(normalizedMerchantName);
			if (cleanedMerchantName != null) {
				return cleanedMerchantName;
			}
		}
		if (normalizedUsername != null) {
			String cleanedUsername = cleanupLegacyMerchantName(normalizedUsername);
			if (cleanedUsername != null) {
				return cleanedUsername;
			}
			return normalizedUsername;
		}
		return normalizedMerchantName;
	}

	private String cleanupLegacyMerchantName(String rawValue) {
		if (rawValue == null) {
			return null;
		}
		Matcher matcher = LEGACY_REGISTERED_MERCHANT_NAME.matcher(rawValue);
		if (matcher.matches()) {
			String prefix = normalizeNullable(matcher.group(1));
			return prefix == null ? rawValue : prefix;
		}
		return rawValue;
	}

	private List<Long> activeSpuIds(Long merchantId) {
		return productSPUMapper.selectList(new LambdaQueryWrapper<ProductSPU>()
			.eq(ProductSPU::getMerchantId, merchantId)
			.eq(ProductSPU::getIsActive, 1))
			.stream().map(ProductSPU::getId).toList();
	}

	private BigDecimal totalRevenue(Long merchantId) {
		List<Long> spuIds = activeSpuIds(merchantId);
		if (spuIds.isEmpty()) {
			return BigDecimal.ZERO;
		}
		MerchantAggregateResponse aggregate = orderDirectory.merchantAggregates(spuIds, null, null, null);
		return aggregate == null || aggregate.getTotalRevenue() == null ? BigDecimal.ZERO : aggregate.getTotalRevenue();
	}

	private BigDecimal calculateTrendPercent(BigDecimal currentRevenue, BigDecimal previousRevenue) {
		if (previousRevenue.compareTo(BigDecimal.ZERO) <= 0) {
			return currentRevenue.compareTo(BigDecimal.ZERO) > 0
				? new BigDecimal("100.0")
				: BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
		}
		return currentRevenue.subtract(previousRevenue)
			.multiply(new BigDecimal("100"))
			.divide(previousRevenue, 1, RoundingMode.HALF_UP);
	}


	private BigDecimal safeAmount(BigDecimal amount) {
		return amount == null ? BigDecimal.ZERO : amount;
	}

	private void assertMerchantNameChangeAllowed(Merchant merchant) {
		LocalDateTime lastChangeTime = merchant.getLastMerchantNameChangeTime();
		if (lastChangeTime != null && lastChangeTime.plusMonths(1).isAfter(LocalDateTime.now())) {
			throw BusinessException.badRequest("商家名称每自然月仅可修改一次，请稍后再试");
		}
	}

	private void assertMerchantNameUnique(String merchantName, Long merchantId) {
		Long count = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
			.eq(Merchant::getMerchantName, merchantName)
			.ne(merchantId != null, Merchant::getId, merchantId));
		if (count != null && count > 0) {
			throw BusinessException.conflict("该商家名称已被占用");
		}
	}

	private void assertPlainTextDescription(String description) {
		if (description == null) {
			return;
		}
		int length = description.codePointCount(0, description.length());
		if (length > 500) {
			throw BusinessException.badRequest("商家简介不超过 500 字");
		}
		if (description.indexOf('<') >= 0 || description.indexOf('>') >= 0) {
			throw BusinessException.badRequest("商家简介仅允许纯文字，不能包含尖括号等标记");
		}
		description.codePoints().forEach(codePoint -> {
			if (codePoint == '\n' || codePoint == '\r' || codePoint == '\t') {
				return;
			}
			if (codePoint < 32 || codePoint == 127) {
				throw BusinessException.badRequest("商家简介仅允许纯文字（可含空格与换行），不能包含控制字符");
			}
		});
	}

	private String normalizeEmail(String email) {
		String normalizedEmail = normalizeRequired(email, "邮箱不能为空");
		return normalizedEmail == null ? null : normalizedEmail.toLowerCase(Locale.ROOT);
	}

	private void syncMerchantTags(Long merchantId, List<String> rawTagNames) {
		List<String> tagNames = normalizeTags(rawTagNames);
		List<MerchantTagRel> existingRelations = merchantTagRelMapper.selectList(
			new LambdaQueryWrapper<MerchantTagRel>().eq(MerchantTagRel::getMerchantId, merchantId));

		if (tagNames.isEmpty()) {
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

	private List<String> normalizeTags(List<String> rawTagNames) {
		if (rawTagNames == null || rawTagNames.isEmpty()) {
			return List.of();
		}

		Set<String> dedupedTagNames = new LinkedHashSet<>();
		for (String rawTagName : rawTagNames) {
			String tagName = normalizeNullable(rawTagName);
			if (tagName == null) {
				continue;
			}
			if (tagName.codePointCount(0, tagName.length()) > MAX_TAG_NAME_LENGTH) {
				throw BusinessException.badRequest("单个商家标签名称长度不能超过 " + MAX_TAG_NAME_LENGTH + " 个字符");
			}
			dedupedTagNames.add(tagName);
		}
		if (dedupedTagNames.size() > MAX_TAG_COUNT) {
			throw BusinessException.badRequest("商家标签数量不能超过 " + MAX_TAG_COUNT + " 个");
		}
		return List.copyOf(dedupedTagNames);
	}

	private String normalizeRequired(String value, String message) {
		String normalized = normalizeNullable(value);
		if (normalized == null) {
			throw BusinessException.badRequest(message);
		}
		return normalized;
	}

	private String normalizeNullable(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}