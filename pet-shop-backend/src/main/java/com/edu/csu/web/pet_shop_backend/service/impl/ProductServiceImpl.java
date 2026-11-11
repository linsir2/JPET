package com.edu.csu.web.pet_shop_backend.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSKU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSpuTagRel;
import com.edu.csu.web.pet_shop_backend.entity.model.Tag;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductListDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSkuCreateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSkuUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSpuCreateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSpuUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.BaseProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductBuyerReviewVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductDetailVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductListVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductSkuVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.exception.LoginException;
import com.edu.csu.web.pet_shop_backend.exception.ProductCreateInvalidException;
import com.edu.csu.web.pet_shop_backend.exception.ProductNotFoundException;
import com.edu.csu.web.pet_shop_backend.exception.ProductUpdateInvalidException;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSKUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSpuTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.TagMapper;
import com.edu.csu.web.pet_shop_backend.client.OrderDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.OrderDtos.ReviewItem;
import com.edu.csu.web.pet_shop_backend.client.UserDirectory;
import com.edu.csu.web.pet_shop_backend.service.ProductService;
import com.edu.csu.web.pet_shop_backend.service.TagService;
import com.edu.csu.web.pet_shop_backend.service.support.MerchantCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;

public class ProductServiceImpl implements ProductService {
	private static final String STATUS_ALL = "all";
	private static final String SPU_STATUS_DRAFT = "draft";
	private static final String SKU_STATUS_AVAILABLE = "available";
	private static final String DEFAULT_USER_AVATAR = "/images/logo192.png";
	private static final Set<String> ALLOWED_SPU_STATUS = Set.of("draft", "pending", "rejected", "published", "taken_down");
	private static final Set<String> ALLOWED_SKU_STATUS = Set.of("available", "unavailable");
	private static final TypeReference<Map<String, Object>> ATTRIBUTE_MAP_TYPE = new TypeReference<>() {};

	private final ProductSPUMapper productSPUMapper;
	private final ProductSKUMapper productSKUMapper;
	private final ProductSpuTagRelMapper productSpuTagRelMapper;
	private final TagMapper tagMapper;
	private final OrderDirectory orderDirectory;
	private final UserDirectory userDirectory;
	private final MerchantCacheService merchantCacheService;
	private final ObjectMapper objectMapper;
	private final TagService tagService;

	public ProductServiceImpl(
		ProductSPUMapper productSPUMapper,
		ProductSKUMapper productSKUMapper,
		ProductSpuTagRelMapper productSpuTagRelMapper,
		TagMapper tagMapper,
		OrderDirectory orderDirectory,
		UserDirectory userDirectory,
		MerchantCacheService merchantCacheService,
		ObjectMapper objectMapper,
		TagService tagService
	) {
		this.productSPUMapper = productSPUMapper;
		this.productSKUMapper = productSKUMapper;
		this.productSpuTagRelMapper = productSpuTagRelMapper;
		this.tagMapper = tagMapper;
		this.orderDirectory = orderDirectory;
		this.userDirectory = userDirectory;
		this.merchantCacheService = merchantCacheService;
		this.objectMapper = objectMapper;
		this.tagService = tagService;
	}


    /**
     * 商户查询自己的商品列表，支持分页、按状态过滤、按名称/标签搜索
     * @param productListDTO 查询参数，包括分页信息、状态过滤、搜索关键词和标签列表
     * @param userId 当前登录用户的 ID（从 JWT 中提取），用于确定商户身份
     * @return 包含商品列表和总页数的 ProductListVO 对象
     */
	@Override
	public ProductListVo listProducts(ProductListDto productListDTO, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
        
        // 规范化查询参数
		ProductListDto safeDTO = normalizeQuery(productListDTO);

        // 从缓存中获取商户 ID
		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

        // 构建查询条件
		LambdaQueryWrapper<ProductSPU> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(ProductSPU::getMerchantId, merchantId)
			.eq(ProductSPU::getIsActive, 1)
			.orderByDesc(ProductSPU::getUpdateTime);

		if (!STATUS_ALL.equalsIgnoreCase(safeDTO.getStatus())) {
			queryWrapper.eq(ProductSPU::getStatus, safeDTO.getStatus());
		}

		// 关键词搜索支持同时匹配商品名称和标签
		List<Long> keywordMatchedSpuIds = List.of();
		if (!safeDTO.getKeyword().isBlank()) {
			keywordMatchedSpuIds = getSpuIdsByTagKeyword(safeDTO.getKeyword());
			List<Long> finalKeywordMatchedSpuIds = keywordMatchedSpuIds;
			queryWrapper.and(wrapper -> {
				wrapper.like(ProductSPU::getName, safeDTO.getKeyword().trim());
				if (!finalKeywordMatchedSpuIds.isEmpty()) {
					wrapper.or().in(ProductSPU::getId, finalKeywordMatchedSpuIds);
				}
			});
		}

		// 标签过滤
		if (!safeDTO.getTags().isEmpty()) {
			List<Long> tagFilteredSpuIds = getSpuIdsByTagNames(safeDTO.getTags());
			if (tagFilteredSpuIds.isEmpty()) {
				return new ProductListVo(List.of(), 0);
			}
			queryWrapper.in(ProductSPU::getId, tagFilteredSpuIds);
		}

        // 分页查询
		Page<ProductSPU> pageRequest = new Page<>(safeDTO.getPage(), safeDTO.getSize());
		Page<ProductSPU> resultPage = productSPUMapper.selectPage(pageRequest, queryWrapper);

        // 构建结果
		List<BaseProductSPU> products = resultPage.getRecords().stream()
			.map(spu -> new BaseProductSPU(spu.getId(), spu.getName(), spu.getImageUrl(), spu.getMinPrice(), spu.getSales()))
			.toList();

		long pages = resultPage.getPages();
		if (pages <= 0) {
			long total = resultPage.getTotal();
			if (total > 0) {
				pages = (total + safeDTO.getSize() - 1) / safeDTO.getSize();
			} else if (!products.isEmpty()) {
				pages = 1;
			}
		}

		return new ProductListVo(products, Math.toIntExact(pages));
	}

	@Override
	public ProductDetailVo getProductDetail(Long productId, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
		if (Objects.isNull(productId) || productId <= 0) {
			throw new ProductNotFoundException(productId, null);
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

		// 获取商品 SPU
		ProductSPU spu = productSPUMapper.selectOne(
			new LambdaQueryWrapper<ProductSPU>()
				.eq(ProductSPU::getId, productId)
				.eq(ProductSPU::getMerchantId, merchantId)
				.eq(ProductSPU::getIsActive, 1)
		);
		if (Objects.isNull(spu)) {
			throw new ProductNotFoundException(productId, merchantId);
		}

		// 获取商品标签列表
		List<String> tags = getTagNamesBySpuId(productId);

		// 获取商品 SKU 列表
		List<ProductSkuVo> skuVOs = getSkuVOsBySpuId(productId);

		// 获取核心属性列表
		List<String> coreAttributes = toStringList(spu.getCoreAttributes());

		// 获取商品对应的真实买家评价
		List<ProductBuyerReviewVo> buyerReviews = getBuyerReviewsBySpuId(productId);

		return new ProductDetailVo(
			spu.getId(),
			spu.getName(),
			spu.getDescription(),
			spu.getImageUrl(),
			spu.getStatus(),
			spu.getAuditReason(),
			tags,
			spu.getMinPrice(),
			spu.getSales(),
			coreAttributes,
			skuVOs,
			buyerReviews,
			spu.getCreateTime(),
			spu.getUpdateTime()
		);
	}

	private List<ProductBuyerReviewVo> getBuyerReviewsBySpuId(Long productId) {
		List<ReviewItem> reviews = orderDirectory.reviews(productId);
		if (reviews.isEmpty()) {
			return List.of();
		}
		List<Long> userIds = reviews.stream().map(ReviewItem::getUserId).filter(Objects::nonNull).distinct().toList();
		Map<Long, UserBriefVo> userMap = userDirectory.batch(userIds);
		return reviews.stream()
			.map(review -> {
				UserBriefVo user = userMap.get(review.getUserId());
				String username = (user == null || "deleted".equals(user.getStatus())) ? "匿名用户" : user.getUsername();
				return new ProductBuyerReviewVo(
					review.getId(),
					username,
					resolveUserAvatar(user),
					review.getRating(),
					review.getContent(),
					review.getReviewTime()
				);
			})
			.toList();
	}

	private String resolveUserAvatar(UserBriefVo user) {
		if (user == null || user.getProfile() == null || user.getProfile().isBlank()) {
			return DEFAULT_USER_AVATAR;
		}
		try {
			JsonNode profileNode = objectMapper.readTree(user.getProfile());
			String avatar = profileNode == null ? null : normalizeNullable(profileNode.path("avatar").asText(null));
			return avatar == null ? DEFAULT_USER_AVATAR : avatar;
		} catch (Exception ex) {
			return DEFAULT_USER_AVATAR;
		}
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	@Override
	@Transactional
	public Long createProduct(ProductSpuCreateDto createDTO, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}

		ProductSpuCreateDto safeCreateDTO = normalizeCreateRequest(createDTO);

		// 校验请求参数逻辑是否合法（主要校验属性与SKU）
		validateCreateRequest(safeCreateDTO);

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);
		List<ProductSkuCreateDto> safeSkus = safeCreateDTO.getSkus();

		// 计算 SPU 的最低价作为展示价格
		BigDecimal minPrice = safeSkus.stream()
			.map(ProductSkuCreateDto::getPrice)
			.min(Comparator.naturalOrder())
			.orElseThrow(() -> new ProductCreateInvalidException("商品SKU列表不能为空", Map.of("field", "skus")));

		ProductSPU productSPU = new ProductSPU();
		productSPU.setMerchantId(merchantId);
		productSPU.setName(safeCreateDTO.getName());
		productSPU.setDescription(safeCreateDTO.getDescription());
		productSPU.setImageUrl(safeCreateDTO.getImageUrl());
		productSPU.setStatus(normalizeSpuStatus(safeCreateDTO.getStatus()));
		productSPU.setIsActive(1);
		productSPU.setAuditReason(null);
		productSPU.setMinPrice(minPrice);
		productSPU.setCoreAttributes(toJsonArrayOrNull(safeCreateDTO.getCoreAttributes()));

		productSPUMapper.insert(productSPU);

		Long spuId = productSPU.getId();
		for (ProductSkuCreateDto skuCreateDTO : safeSkus) {
			ProductSKU sku = new ProductSKU();
			sku.setSpuId(spuId);
			sku.setAttributeValues(toJsonObject(skuCreateDTO.getAttributeValues()));
			sku.setStatus(normalizeSkuStatus(skuCreateDTO.getStatus()));
			sku.setIsActive(1);
			sku.setImageUrl(isBlank(skuCreateDTO.getImageUrl()) ? null : skuCreateDTO.getImageUrl().trim());
			sku.setPrice(skuCreateDTO.getPrice());
			sku.setStock(skuCreateDTO.getStock());
			productSKUMapper.insert(sku);
		}

		// 同步商品标签关联（tags 为 null 时不操作）
		if (createDTO.getTags() != null) {
			tagService.syncSpuTags(spuId, createDTO.getTags());
		}

		return spuId;
	}

	@Override
	@Transactional
	public void createProductSkus(List<ProductSkuCreateDto> createDTOList, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}

		List<ProductSkuCreateDto> safeSkus = normalizeStandaloneSkus(createDTOList);
		if (safeSkus.isEmpty()) {
			throw new ProductCreateInvalidException("商品SKU列表不能为空", Map.of("field", "skus"));
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);
		Map<Long, ProductSPU> spuMap = loadMerchantSpuMap(safeSkus, merchantId);

		// 预检查：单品SPU只能存在一个空属性SKU，避免重复创建冲突
		validateSingleSpuEmptySkuConflict(safeSkus, spuMap);

		Map<Long, BigDecimal> insertedMinPriceBySpu = new LinkedHashMap<>();
		for (int i = 0; i < safeSkus.size(); i++) {
			ProductSkuCreateDto skuCreateDTO = safeSkus.get(i);
			ProductSPU spu = spuMap.get(skuCreateDTO.getSpuId());

			// 校验SKU的创建与SPU的单品/属性商品逻辑一致性
			validateStandaloneSkuBySpu(spu, skuCreateDTO, i);

			ProductSKU sku = new ProductSKU();
			sku.setSpuId(skuCreateDTO.getSpuId());
			sku.setAttributeValues(toJsonObject(skuCreateDTO.getAttributeValues()));
			sku.setStatus(normalizeSkuStatus(skuCreateDTO.getStatus()));
			sku.setIsActive(1);
			sku.setImageUrl(isBlank(skuCreateDTO.getImageUrl()) ? null : skuCreateDTO.getImageUrl().trim());
			sku.setPrice(skuCreateDTO.getPrice());
			sku.setStock(skuCreateDTO.getStock());
			productSKUMapper.insert(sku);

			// 记录每个SPU新插入的SKU的最低价，后续用于更新SPU的展示价格
			insertedMinPriceBySpu.merge(
				skuCreateDTO.getSpuId(),
				skuCreateDTO.getPrice(),
				(current, candidate) -> current.compareTo(candidate) <= 0 ? current : candidate
			);
		}

		// 更新SPU最低价格
		for (Map.Entry<Long, BigDecimal> entry : insertedMinPriceBySpu.entrySet()) {
			ProductSPU spu = spuMap.get(entry.getKey());
			BigDecimal currentMinPrice = spu.getMinPrice();
			BigDecimal insertedMinPrice = entry.getValue();
			if (currentMinPrice == null || insertedMinPrice.compareTo(currentMinPrice) < 0) {
				ProductSPU updatingSpu = new ProductSPU();
				updatingSpu.setId(spu.getId());
				updatingSpu.setMinPrice(insertedMinPrice);
				productSPUMapper.updateById(updatingSpu);
			}
		}
	}

	@Override
	@Transactional
	public void updateProduct(ProductSpuUpdateDto updateDTO, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
		if (updateDTO == null || updateDTO.getId() == null) {
			throw new ProductUpdateInvalidException("商品SPU ID不能为空", Map.of("field", "id"));
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

		// 查询并校验 SPU 归属
		ProductSPU existingSpu = productSPUMapper.selectOne(
			new LambdaQueryWrapper<ProductSPU>()
				.eq(ProductSPU::getId, updateDTO.getId())
				.eq(ProductSPU::getMerchantId, merchantId)
				.eq(ProductSPU::getIsActive, 1)
		);
		if (existingSpu == null) {
			throw new ProductNotFoundException(updateDTO.getId(), merchantId);
		}

		// 获取当前 SPU 的核心属性
		List<String> currentCA = normalizeCoreAttributes(toStringList(existingSpu.getCoreAttributes()));

		// 判断 CA 是否被请求改变：DTO 中 coreAttributes 非 null 且规范化后与当前不同
		boolean caChanged = false;
		List<String> newCA = currentCA;
		if (updateDTO.getCoreAttributes() != null) {
			List<String> normalizedRequestCA = normalizeCoreAttributes(updateDTO.getCoreAttributes());
			if (!normalizedRequestCA.equals(currentCA)) {
				caChanged = true;
				newCA = normalizedRequestCA;
			}
		}

		List<ProductSkuUpdateDto> skuUpdates = updateDTO.getSkus();
		boolean hasSkuUpdates = skuUpdates != null && !skuUpdates.isEmpty();

		if (caChanged) {
			// CA 改变：所有原有 active SKU 全部作废
			deactivateAllSkusBySpuId(existingSpu.getId());

			if (hasSkuUpdates) {
				// 根据新 CA 创建新的 SKU
				createSkusFromUpdateDTOs(skuUpdates, newCA, existingSpu.getId(), merchantId);
			}
		} else {
			// CA 未改变
			if (hasSkuUpdates) {
				// 逐个处理需要更新的 SKU：旧的作废，创建新的
				updateSkusByDTOs(skuUpdates, currentCA, existingSpu.getId(), merchantId);
			}
		}

		// 更新 SPU 基本字段
		ProductSPU updatingSpu = new ProductSPU();
		updatingSpu.setId(existingSpu.getId());
		if (!isBlank(updateDTO.getName())) {
			updatingSpu.setName(updateDTO.getName().trim());
		}
		if (updateDTO.getDescription() != null) {
			updatingSpu.setDescription(updateDTO.getDescription().trim());
		}
		if (!isBlank(updateDTO.getImageUrl())) {
			updatingSpu.setImageUrl(updateDTO.getImageUrl().trim());
		}
		if (!isBlank(updateDTO.getStatus())) {
			updatingSpu.setStatus(normalizeSpuStatus(updateDTO.getStatus()));
		}
		if (caChanged) {
			updatingSpu.setCoreAttributes(toJsonArrayOrNull(newCA));
		}

		// 重新计算 minPrice
		BigDecimal newMinPrice = recalculateMinPrice(existingSpu.getId());
		updatingSpu.setMinPrice(newMinPrice);

		productSPUMapper.updateById(updatingSpu);

		// 同步商品标签关联（tags 为 null 时不操作）
		if (updateDTO.getTags() != null) {
			tagService.syncSpuTags(existingSpu.getId(), updateDTO.getTags());
		}
	}

	@Override
	@Transactional
	public void updateProductSkus(List<ProductSkuUpdateDto> updateDTOList, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
		if (updateDTOList == null || updateDTOList.isEmpty()) {
			throw new ProductUpdateInvalidException("SKU更新列表不能为空", Map.of("field", "skus"));
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

		// 预加载所有涉及的旧 SKU 并校验归属
		Map<Long, ProductSKU> oldSkuMap = new LinkedHashMap<>();
		Set<Long> affectedSpuIds = new LinkedHashSet<>();
		for (int i = 0; i < updateDTOList.size(); i++) {
			ProductSkuUpdateDto dto = updateDTOList.get(i);
			if (dto == null) {
				throw new ProductUpdateInvalidException("SKU更新项不能为null", Map.of("field", "skus", "index", i));
			}
			if (dto.getId() == null) {
				throw new ProductUpdateInvalidException("SKU ID不能为空", Map.of("field", "skus[" + i + "].id"));
			}

			ProductSKU oldSku = productSKUMapper.selectById(dto.getId());
			if (oldSku == null) {
				throw new ProductUpdateInvalidException(
					"SKU不存在", Map.of("field", "skus[" + i + "].id", "skuId", dto.getId()));
			}
			oldSkuMap.put(dto.getId(), oldSku);
			affectedSpuIds.add(oldSku.getSpuId());
		}

		// 校验所有涉及的 SPU 归属当前商户
		Map<Long, ProductSPU> spuMap = new LinkedHashMap<>();
		for (Long spuId : affectedSpuIds) {
			ProductSPU spu = productSPUMapper.selectOne(
				new LambdaQueryWrapper<ProductSPU>()
					.eq(ProductSPU::getId, spuId)
					.eq(ProductSPU::getMerchantId, merchantId)
					.eq(ProductSPU::getIsActive, 1)
			);
			if (spu == null) {
				throw new ProductNotFoundException(spuId, merchantId);
			}
			spuMap.put(spuId, spu);
		}

		// 逐个处理 SKU 更新
		for (int i = 0; i < updateDTOList.size(); i++) {
			ProductSkuUpdateDto dto = updateDTOList.get(i);
			ProductSKU oldSku = oldSkuMap.get(dto.getId());
			Long spuId = oldSku.getSpuId();
			ProductSPU spu = spuMap.get(spuId);

			// 以当前 SPU 的 coreAttributes 为准
			List<String> currentCA = normalizeCoreAttributes(toStringList(spu.getCoreAttributes()));

			// 旧 SKU 作废
			deactivateSkuById(oldSku.getId());

			// 以旧 SKU 为模板，DTO 非 null 字段覆盖，创建新 SKU
			ProductSKU newSku = buildNewSkuFromOld(oldSku, dto, spuId);

			// 校验新 SKU 的属性与当前 CA 一致
			Map<String, Object> attrValues = normalizeAttributeValues(
				objectMapper.convertValue(newSku.getAttributeValues(), ATTRIBUTE_MAP_TYPE)
			);
			if (currentCA.isEmpty()) {
				if (attrValues != null && !attrValues.isEmpty()) {
					throw new ProductUpdateInvalidException(
						"单品SPU的SKU属性必须为空对象{}",
						Map.of("field", "skus[" + i + "].attributeValues", "spuId", spuId)
					);
				}
			} else {
				validateSkuAttributesAgainstCA(attrValues, currentCA, i);
			}

			newSku.setAttributeValues(toJsonObject(attrValues));
			productSKUMapper.insert(newSku);
		}

		// 更新所有涉及的 SPU 的 minPrice
		for (Long spuId : affectedSpuIds) {
			BigDecimal newMinPrice = recalculateMinPrice(spuId);
			ProductSPU updatingSpu = new ProductSPU();
			updatingSpu.setId(spuId);
			updatingSpu.setMinPrice(newMinPrice);
			productSPUMapper.updateById(updatingSpu);
		}
	}

	@Override
	@Transactional
	public void deleteProductSpus(List<Long> spuIds, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
		if (spuIds == null || spuIds.isEmpty()) {
			throw new ProductUpdateInvalidException("SPU ID列表不能为空", Map.of("field", "spuIds"));
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

		// 校验所有 SPU 归属当前商户且为 active
		List<ProductSPU> spus = productSPUMapper.selectList(
			new LambdaQueryWrapper<ProductSPU>()
				.eq(ProductSPU::getMerchantId, merchantId)
				.eq(ProductSPU::getIsActive, 1)
				.in(ProductSPU::getId, spuIds)
		);

		if (spus.size() != spuIds.stream().distinct().count()) {
			// 找出不存在的 SPU ID
			Set<Long> foundIds = spus.stream().map(ProductSPU::getId).collect(java.util.stream.Collectors.toSet());
			for (Long spuId : spuIds) {
				if (!foundIds.contains(spuId)) {
					throw new ProductNotFoundException(spuId, merchantId);
				}
			}
		}

		// 软删除 SPU：设置 is_active=0
		for (ProductSPU spu : spus) {
			ProductSPU deactivating = new ProductSPU();
			deactivating.setId(spu.getId());
			deactivating.setIsActive(0);
			productSPUMapper.updateById(deactivating);

			// 联动软删除关联的所有 active SKU
			deactivateAllSkusBySpuId(spu.getId());
		}
	}

	@Override
	@Transactional
	public void deleteProductSkus(List<Long> skuIds, Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}
		if (skuIds == null || skuIds.isEmpty()) {
			throw new ProductUpdateInvalidException("SKU ID列表不能为空", Map.of("field", "skuIds"));
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

		// 预加载所有 SKU 并校验存在性
		Set<Long> affectedSpuIds = new LinkedHashSet<>();
		List<ProductSKU> skusToDelete = new ArrayList<>();
		for (int i = 0; i < skuIds.size(); i++) {
			Long skuId = skuIds.get(i);
			if (skuId == null) {
				throw new ProductUpdateInvalidException("SKU ID不能为null", Map.of("field", "skuIds", "index", i));
			}
			ProductSKU sku = productSKUMapper.selectById(skuId);
			if (sku == null || !Objects.equals(sku.getIsActive(), 1)) {
				throw new ProductUpdateInvalidException(
					"SKU不存在或已被删除", Map.of("field", "skuIds[" + i + "]", "skuId", skuId));
			}
			skusToDelete.add(sku);
			affectedSpuIds.add(sku.getSpuId());
		}

		// 校验所有关联的 SPU 归属当前商户
		for (Long spuId : affectedSpuIds) {
			ProductSPU spu = productSPUMapper.selectOne(
				new LambdaQueryWrapper<ProductSPU>()
					.eq(ProductSPU::getId, spuId)
					.eq(ProductSPU::getMerchantId, merchantId)
			);
			if (spu == null) {
				throw new ProductNotFoundException(spuId, merchantId);
			}
		}

		// 软删除 SKU
		for (ProductSKU sku : skusToDelete) {
			deactivateSkuById(sku.getId());
		}

		// 更新受影响 SPU 的 minPrice
		for (Long spuId : affectedSpuIds) {
			BigDecimal newMinPrice = recalculateMinPrice(spuId);
			ProductSPU updatingSpu = new ProductSPU();
			updatingSpu.setId(spuId);
			updatingSpu.setMinPrice(newMinPrice);
			productSPUMapper.updateById(updatingSpu);
		}
	}



	@Override
	@Transactional
	public void deleteSingleSku(Long skuId, Long userId) {
		if (skuId == null) {
			throw new ProductUpdateInvalidException("SKU ID不能为空", Map.of("field", "skuId"));
		}
		ProductSKU sku = productSKUMapper.selectById(skuId);
		if (sku == null || !Objects.equals(sku.getIsActive(), 1)) {
			deleteProductSkus(List.of(skuId), userId);
			return;
		}
		ProductSPU spu = productSPUMapper.selectById(sku.getSpuId());
		if (spu != null && "published".equals(spu.getStatus())) {
			Long activeCount = productSKUMapper.selectCount(new LambdaQueryWrapper<ProductSKU>()
				.eq(ProductSKU::getSpuId, sku.getSpuId())
				.eq(ProductSKU::getIsActive, 1));
			if (activeCount != null && activeCount <= 1) {
				throw BusinessException.conflict("已上架商品至少保留一个可售规格");
			}
		}
		deleteProductSkus(List.of(skuId), userId);
	}

	@Override
	public List<String> getUsingTags(Long userId) {
		if (Objects.isNull(userId)) {
			throw new LoginException("登录信息已失效，请重新登录", null);
		}

		Long merchantId = merchantCacheService.getMerchantIdByUserId(userId);

		// 查询该商户所有活跃的 SPU ID
		List<ProductSPU> activeSpus = productSPUMapper.selectList(
			new LambdaQueryWrapper<ProductSPU>()
				.select(ProductSPU::getId)
				.eq(ProductSPU::getMerchantId, merchantId)
				.eq(ProductSPU::getIsActive, 1)
		);
		List<Long> spuIds = activeSpus.stream().map(ProductSPU::getId).toList();

		return tagService.getTagNamesBySpuIds(spuIds);
	}



    // ==================== 辅助方法 ====================

    /**
     * 对 ProductListDTO 中的查询参数进行规范化处理，设置默认值并去除多余空格
     * @param productListDTO 查询参数对象
     * @return
     */
	private ProductListDto normalizeQuery(ProductListDto productListDTO) {
		ProductListDto source = Objects.isNull(productListDTO) ? new ProductListDto() : productListDTO;

		Integer rawPage = source.getPage();
		Integer rawSize = source.getSize();
		int page = Objects.isNull(rawPage) || rawPage <= 0 ? 1 : rawPage;
		int size = Objects.isNull(rawSize) || rawSize <= 0 ? 10 : rawSize;
		String status = Objects.isNull(source.getStatus()) || source.getStatus().isBlank()
			? STATUS_ALL
			: source.getStatus().trim();
		String keyword = Objects.isNull(source.getKeyword()) ? "" : source.getKeyword().trim();

		List<String> tags = Objects.isNull(source.getTags())
			? List.of()
			: source.getTags().stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(tag -> !tag.isBlank())
				.distinct()
				.toList();

		return new ProductListDto(page, size, status, new ArrayList<>(tags), keyword);
	}

	private ProductSpuCreateDto normalizeCreateRequest(ProductSpuCreateDto createDTO) {
		if (createDTO == null) {
			throw new ProductCreateInvalidException("请求体不能为空", Map.of("field", "requestBody"));
		}

		String status = normalizeSpuStatus(createDTO.getStatus());
		List<String> coreAttributes = normalizeCoreAttributes(createDTO.getCoreAttributes());
		List<ProductSkuCreateDto> skus = normalizeSkus(createDTO.getSkus());

		return new ProductSpuCreateDto(
			createDTO.getName(),
			createDTO.getDescription(),
			createDTO.getImageUrl(),
			status,
			coreAttributes,
			createDTO.getTags(),
			skus
		);
	}

	private List<String> normalizeCoreAttributes(List<String> coreAttributes) {
		if (coreAttributes == null || coreAttributes.isEmpty()) {
			return List.of();
		}

		return coreAttributes.stream()
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.distinct()
			.toList();
	}

	private List<ProductSkuCreateDto> normalizeSkus(List<ProductSkuCreateDto> skus) {
		if (skus == null || skus.isEmpty()) {
			return List.of();
		}

		List<ProductSkuCreateDto> normalized = new ArrayList<>();
		for (ProductSkuCreateDto sku : skus) {
			if (sku == null) {
				normalized.add(null);
				continue;
			}

			Map<String, Object> attributeValues = normalizeAttributeValues(sku.getAttributeValues());
			normalized.add(new ProductSkuCreateDto(
				null,
				attributeValues,
				normalizeSkuStatus(sku.getStatus()),
				sku.getImageUrl(),
				sku.getPrice(),
				sku.getStock()
			));
		}
		return normalized;
	}

	private List<ProductSkuCreateDto> normalizeStandaloneSkus(List<ProductSkuCreateDto> skus) {
		if (skus == null || skus.isEmpty()) {
			return List.of();
		}

		List<ProductSkuCreateDto> normalized = new ArrayList<>();
		for (int i = 0; i < skus.size(); i++) {
			ProductSkuCreateDto sku = skus.get(i);
			if (sku == null) {
				throw new ProductCreateInvalidException("商品SKU不能为null", Map.of("field", "skus", "index", i));
			}

			Long spuId = sku.getSpuId();
			if (spuId == null || spuId <= 0) {
				throw new ProductCreateInvalidException(
					"SKU单独创建时spuId不能为空且必须大于0",
					Map.of("field", "skus[" + i + "].spuId", "value", spuId)
				);
			}

			normalized.add(new ProductSkuCreateDto(
				spuId,
				normalizeAttributeValues(sku.getAttributeValues()),
				normalizeSkuStatus(sku.getStatus()),
				sku.getImageUrl(),
				sku.getPrice(),
				sku.getStock()
			));
		}
		return normalized;
	}

	private Map<String, Object> normalizeAttributeValues(Map<String, Object> attributeValues) {
		if (attributeValues == null) {
			return null;
		}

		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : attributeValues.entrySet()) {
			String key = entry.getKey() == null ? null : entry.getKey().trim();
			normalized.put(key, entry.getValue());
		}
		return normalized;
	}

	private void validateCreateRequest(ProductSpuCreateDto createDTO) {
		List<ProductSkuCreateDto> skus = createDTO.getSkus();
		if (skus.isEmpty()) {
			throw new ProductCreateInvalidException("商品SKU列表不能为空", Map.of("field", "skus"));
		}

		for (int i = 0; i < skus.size(); i++) {
			if (skus.get(i) == null) {
				throw new ProductCreateInvalidException("商品SKU不能为null", Map.of("field", "skus", "index", i));
			}
		}

		List<String> coreAttributes = createDTO.getCoreAttributes();
		if (coreAttributes.isEmpty()) {
			validateSingleSkuWithoutAttributes(skus);
			return;
		}

		validateAttributeBranchSkus(coreAttributes, skus);
	}

	private void validateSingleSkuWithoutAttributes(List<ProductSkuCreateDto> skus) {
		if (skus.size() != 1) {
			throw new ProductCreateInvalidException(
				"单品SPU必须只包含一个SKU",
				Map.of("field", "skus", "expectedSize", 1, "actualSize", skus.size())
			);
		}

		Map<String, Object> attributeValues = skus.get(0).getAttributeValues();
		if (attributeValues == null || !attributeValues.isEmpty()) {
			throw new ProductCreateInvalidException(
				"单品SPU的SKU属性必须为空对象{}",
				Map.of("field", "skus[0].attributeValues")
			);
		}
	}

	private void validateAttributeBranchSkus(List<String> coreAttributes, List<ProductSkuCreateDto> skus) {
		Set<String> expectedAttributeNames = new LinkedHashSet<>(coreAttributes);

		for (int i = 0; i < skus.size(); i++) {
			Map<String, Object> attributeValues = skus.get(i).getAttributeValues();
			if (attributeValues == null || attributeValues.isEmpty()) {
				throw new ProductCreateInvalidException(
					"属性商品SPU中每个SKU都必须包含attributeValues",
					Map.of("field", "skus[" + i + "].attributeValues")
				);
			}

			Set<String> actualAttributeNames = new LinkedHashSet<>(attributeValues.keySet());
			if (actualAttributeNames.contains(null) || actualAttributeNames.stream().anyMatch(String::isBlank)) {
				throw new ProductCreateInvalidException(
					"SKU属性名不能为空",
					Map.of("field", "skus[" + i + "].attributeValues")
				);
			}

			if (!actualAttributeNames.equals(expectedAttributeNames)) {
				throw new ProductCreateInvalidException(
					"SKU属性名与SPU核心属性不一致",
					Map.of(
						"field", "skus[" + i + "].attributeValues",
						"expected", expectedAttributeNames,
						"actual", actualAttributeNames
					)
				);
			}

			for (String attributeName : expectedAttributeNames) {
				Object attributeValue = attributeValues.get(attributeName);
				if (attributeValue == null) {
					throw new ProductCreateInvalidException(
						"SKU属性值不能为空",
						Map.of("field", "skus[" + i + "].attributeValues." + attributeName)
					);
				}
				if (attributeValue instanceof CharSequence text && text.toString().trim().isBlank()) {
					throw new ProductCreateInvalidException(
						"SKU属性值不能为空字符串",
						Map.of("field", "skus[" + i + "].attributeValues." + attributeName)
					);
				}
			}
		}
	}

	private Map<Long, ProductSPU> loadMerchantSpuMap(List<ProductSkuCreateDto> skus, Long merchantId) {
		Set<Long> spuIds = new LinkedHashSet<>();
		for (ProductSkuCreateDto sku : skus) {
			spuIds.add(sku.getSpuId());
		}

		// 获取当前商户且在请求中的SPU
		List<ProductSPU> spus = productSPUMapper.selectList(
			new LambdaQueryWrapper<ProductSPU>()
				.eq(ProductSPU::getMerchantId, merchantId)
				.eq(ProductSPU::getIsActive, 1)
				.in(ProductSPU::getId, spuIds)
		);

		Map<Long, ProductSPU> spuMap = new LinkedHashMap<>();
		for (ProductSPU spu : spus) {
			spuMap.put(spu.getId(), spu);
		}

		if (spuMap.size() != spuIds.size()) {
			for (Long spuId : spuIds) {
				if (!spuMap.containsKey(spuId)) {
					throw new ProductNotFoundException(spuId, merchantId);
				}
			}
		}

		return spuMap;
	}

	private void validateSingleSpuEmptySkuConflict(List<ProductSkuCreateDto> skus, Map<Long, ProductSPU> spuMap) {
		// 计算请求中每个被请求的SPU的空属性SKU数量
		Map<Long, Integer> requestEmptySkuCountBySpu = new LinkedHashMap<>();

		for (ProductSkuCreateDto sku : skus) {
			ProductSPU spu = spuMap.get(sku.getSpuId());
			List<String> coreAttributes = normalizeCoreAttributes(toStringList(spu.getCoreAttributes()));
			if (!coreAttributes.isEmpty()) {
				continue;
			}

			Map<String, Object> attributeValues = sku.getAttributeValues();
			if (attributeValues != null && attributeValues.isEmpty()) {
				requestEmptySkuCountBySpu.merge(sku.getSpuId(), 1, Integer::sum); // 空属性SKU数量加1
			}
		}

		// 校验请求中每个单品SPU的空属性SKU数量是否合法（应为 0）
		for (Map.Entry<Long, Integer> entry : requestEmptySkuCountBySpu.entrySet()) {
			if (entry.getValue() > 0) {
				throw new ProductCreateInvalidException(
					"单品SPU只允许存在一个空属性SKU，请求中出现重复创建",
					Map.of("field", "skus", "spuId", entry.getKey())
				);
			}
		}
	}

	/**
	 * 校验SKU的创建与SPU是否为单品或属性商品的逻辑一致性
	 * @param spu
	 * @param sku
	 * @param index
	 */
	private void validateStandaloneSkuBySpu(ProductSPU spu, ProductSkuCreateDto sku, int index) {
		List<String> coreAttributes = normalizeCoreAttributes(toStringList(spu.getCoreAttributes()));
		Map<String, Object> attributeValues = sku.getAttributeValues();

		// 单品SPU不允许SKU包含任何属性值，只允许为 "{}" 空SKU
		if (coreAttributes.isEmpty()) {
			if (attributeValues == null || !attributeValues.isEmpty()) {
				throw new ProductCreateInvalidException(
					"目标SPU是单品，SKU属性必须为空对象{}",
					Map.of("field", "skus[" + index + "].attributeValues", "spuId", sku.getSpuId())
				);
			}
			return;
		}

		// 非单品SPU要求SKU必须提供属性值，且属性名必须与SPU核心属性一致，属性值不能为空
		if (attributeValues == null || attributeValues.isEmpty()) {
			throw new ProductCreateInvalidException(
				"目标SPU包含核心属性，SKU必须提供attributeValues",
				Map.of("field", "skus[" + index + "].attributeValues", "spuId", sku.getSpuId())
			);
		}

		Set<String> expectedAttributeNames = new LinkedHashSet<>(coreAttributes);
		Set<String> actualAttributeNames = new LinkedHashSet<>(attributeValues.keySet());
		if (actualAttributeNames.contains(null) || actualAttributeNames.stream().anyMatch(String::isBlank)) {
			throw new ProductCreateInvalidException(
				"SKU属性名不能为空",
				Map.of("field", "skus[" + index + "].attributeValues", "spuId", sku.getSpuId())
			);
		}

		if (!actualAttributeNames.equals(expectedAttributeNames)) {
			throw new ProductCreateInvalidException(
				"SKU属性名与目标SPU核心属性不一致",
				Map.of(
					"field", "skus[" + index + "].attributeValues",
					"spuId", sku.getSpuId(),
					"expected", expectedAttributeNames,
					"actual", actualAttributeNames
				)
			);
		}

		for (String attributeName : expectedAttributeNames) {
			Object attributeValue = attributeValues.get(attributeName);
			if (attributeValue == null) {
				throw new ProductCreateInvalidException(
					"SKU属性值不能为空",
					Map.of("field", "skus[" + index + "].attributeValues." + attributeName, "spuId", sku.getSpuId())
				);
			}
			if (attributeValue instanceof CharSequence text && text.toString().trim().isBlank()) {
				throw new ProductCreateInvalidException(
					"SKU属性值不能为空字符串",
					Map.of("field", "skus[" + index + "].attributeValues." + attributeName, "spuId", sku.getSpuId())
				);
			}
		}
	}

	private JsonNode toJsonArrayOrNull(List<String> values) {
		if (values == null || values.isEmpty()) {
			return null;
		}
		return objectMapper.valueToTree(values);
	}

	private JsonNode toJsonObject(Map<String, Object> values) {
		if (values == null) {
			return null;
		}
		return objectMapper.valueToTree(values);
	}

	private String normalizeSpuStatus(String status) {
		if (isBlank(status)) {
			return SPU_STATUS_DRAFT;
		}

		String normalized = status.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_SPU_STATUS.contains(normalized)) {
			throw new ProductCreateInvalidException("商品状态不合法", Map.of("field", "status", "value", status));
		}
		return normalized;
	}

	private String normalizeSkuStatus(String status) {
		if (isBlank(status)) {
			return SKU_STATUS_AVAILABLE;
		}

		String normalized = status.trim().toLowerCase(Locale.ROOT);
		if (!ALLOWED_SKU_STATUS.contains(normalized)) {
			throw new ProductCreateInvalidException("SKU状态不合法", Map.of("field", "skus.status", "value", status));
		}
		return normalized;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * 根据 SPU ID 获取关联的标签名称列表
	 * @param spuId
	 * @return
	 */
	private List<String> getTagNamesBySpuId(Long spuId) {
		List<ProductSpuTagRel> rels = productSpuTagRelMapper.selectList(
			new LambdaQueryWrapper<ProductSpuTagRel>()
				.eq(ProductSpuTagRel::getSpuId, spuId)
				.orderByAsc(ProductSpuTagRel::getId)
		);
		if (rels.isEmpty()) {
			return List.of();
		}

		List<Long> tagIds = rels.stream().map(ProductSpuTagRel::getTagId).distinct().toList();
		List<Tag> tags = tagMapper.selectList(
			new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds)
		);
		if (tags == null || tags.isEmpty()) {
			return List.of();
		}
		return tags.stream().map(Tag::getName).filter(Objects::nonNull).toList();
	}

	/**
	 * 根据标签关键词搜索匹配的 SPU ID 列表，支持模糊匹配标签名称
	 * @param keyword
	 * @return
	 */
	private List<Long> getSpuIdsByTagKeyword(String keyword) {
		String trimmedKeyword = keyword == null ? "" : keyword.trim();
		if (trimmedKeyword.isBlank()) {
			return List.of();
		}

		List<Tag> matchedTags = tagMapper.selectList(
			new LambdaQueryWrapper<Tag>().like(Tag::getName, trimmedKeyword)
		);
		if (matchedTags.isEmpty()) {
			return List.of();
		}

		List<Long> tagIds = matchedTags.stream().map(Tag::getId).filter(Objects::nonNull).distinct().toList();
		return getSpuIdsByTagIds(tagIds);
	}

	/**
	 * 根据标签名称列表获取匹配的 SPU ID 列表，支持多个标签同时过滤（取交集）
	 * @param tagNames 标签名称列表
	 * @return 匹配的 SPU ID 列表
	 */
	private List<Long> getSpuIdsByTagNames(List<String> tagNames) {
		if (tagNames == null || tagNames.isEmpty()) {
			return List.of();
		}

		List<Tag> matchedTags = tagMapper.selectList(
			new LambdaQueryWrapper<Tag>().in(Tag::getName, tagNames)
		);
		if (matchedTags.isEmpty()) {
			return List.of();
		}

		List<Long> tagIds = matchedTags.stream().map(Tag::getId).filter(Objects::nonNull).distinct().toList();
		return getSpuIdsByTagIds(tagIds);
	}

	/**
	 * 根据标签 ID 列表获取匹配的 SPU ID 列表，支持多个标签同时过滤（取交集）
	 * @param tagIds 标签 ID 列表
	 * @return 匹配的 SPU ID 列表
	 */
	private List<Long> getSpuIdsByTagIds(List<Long> tagIds) {
		if (tagIds == null || tagIds.isEmpty()) {
			return List.of();
		}

		List<ProductSpuTagRel> rels = productSpuTagRelMapper.selectList(
			new LambdaQueryWrapper<ProductSpuTagRel>().in(ProductSpuTagRel::getTagId, tagIds)
		);
		if (rels.isEmpty()) {
			return List.of();
		}

		Set<Long> spuIds = new LinkedHashSet<>();
		for (ProductSpuTagRel rel : rels) {
			if (rel != null && rel.getSpuId() != null) {
				spuIds.add(rel.getSpuId());
			}
		}
		return new ArrayList<>(spuIds);
	}

	/**
	 * 根据 SPU ID 获取关联的 SKU 列表，并转换为 ProductSkuVo 对象列表
	 * @param spuId
	 * @return
	 */
	private List<ProductSkuVo> getSkuVOsBySpuId(Long spuId) {
		List<ProductSKU> skus = productSKUMapper.selectList(
			new LambdaQueryWrapper<ProductSKU>()
				.eq(ProductSKU::getSpuId, spuId)
				.eq(ProductSKU::getIsActive, 1)
				.orderByAsc(ProductSKU::getId)
		);

		return skus.stream().map(sku -> new ProductSkuVo(
			sku.getId(),
			toObjectMap(sku.getAttributeValues()),
			sku.getStatus(),
			sku.getImageUrl(),
			sku.getPrice(),
			sku.getStock(),
			sku.getCreateTime(),
			sku.getUpdateTime()
		)).toList();
	}

	private Map<String, Object> toObjectMap(JsonNode jsonNode) {
		if (jsonNode == null || jsonNode.isNull()) {
			return Collections.emptyMap();
		}
		return objectMapper.convertValue(jsonNode, ATTRIBUTE_MAP_TYPE);
	}

	private List<String> toStringList(JsonNode jsonNode) {
		if (jsonNode == null || jsonNode.isNull() || !jsonNode.isArray()) {
			return List.of();
		}

		List<String> result = new ArrayList<>();
		for (JsonNode node : jsonNode) {
			if (node != null && !node.isNull()) {
				result.add(node.asText());
			}
		}
		return result;
	}

	// ==================== 商品更新辅助方法 ====================

	/**
	 * 批量将某 SPU 下所有 active SKU 设为 is_active=0（软删除）
	 * @param spuId SPU主键ID
	 */
	private void deactivateAllSkusBySpuId(Long spuId) {
		productSKUMapper.update(null,
			new LambdaUpdateWrapper<ProductSKU>()
				.eq(ProductSKU::getSpuId, spuId)
				.eq(ProductSKU::getIsActive, 1)
				.set(ProductSKU::getIsActive, 0)
		);
	}

	/**
	 * 将单个 SKU 设为 is_active=0（软删除）
	 * @param skuId SKU主键ID
	 */
	private void deactivateSkuById(Long skuId) {
		ProductSKU deactivating = new ProductSKU();
		deactivating.setId(skuId);
		deactivating.setIsActive(0);
		productSKUMapper.updateById(deactivating);
	}

	/**
	 * CA 改变场景：根据 ProductSkuUpdateDTO 列表创建新的 SKU
	 * DTO 中有 id 的以旧 SKU 为模板（非 null 字段覆盖），无 id 的视为全新创建
	 * @param skuUpdates SKU 更新 DTO 列表
	 * @param newCA 新的核心属性列表
	 * @param spuId SPU主键ID
	 * @param merchantId 商户ID（用于异常信息展示）
	 */
	private void createSkusFromUpdateDTOs(List<ProductSkuUpdateDto> skuUpdates, List<String> newCA,
										  Long spuId, Long merchantId) {
		if (newCA.isEmpty()) {
			// 新 CA 为空 → 变为单品SPU，只允许一个空属性SKU
			validateUpdateSingleSkuWithoutAttributes(skuUpdates);
		}

		for (int i = 0; i < skuUpdates.size(); i++) {
			ProductSkuUpdateDto dto = skuUpdates.get(i);
			if (dto == null) {
				throw new ProductUpdateInvalidException("SKU更新项不能为null", Map.of("field", "skus", "index", i));
			}

			ProductSKU newSku;
			if (dto.getId() != null) {
				// 以旧 SKU 为模板
				ProductSKU oldSku = productSKUMapper.selectById(dto.getId());
				if (oldSku == null || !oldSku.getSpuId().equals(spuId)) {
					throw new ProductUpdateInvalidException(
						"SKU不存在或不属于当前SPU",
						Map.of("field", "skus[" + i + "].id", "skuId", dto.getId(), "spuId", spuId)
					);
				}
				newSku = buildNewSkuFromOld(oldSku, dto, spuId);
			} else {
				// 全新创建
				newSku = buildNewSkuFromDTO(dto, spuId, i);
			}

			// 根据新 CA 校验 SKU 属性
			Map<String, Object> attrValues = normalizeAttributeValues(
				objectMapper.convertValue(newSku.getAttributeValues(), ATTRIBUTE_MAP_TYPE)
			);
			if (newCA.isEmpty()) {
				// 单品：属性必须为空对象
				if (attrValues != null && !attrValues.isEmpty()) {
					throw new ProductUpdateInvalidException(
						"单品SPU的SKU属性必须为空对象{}",
						Map.of("field", "skus[" + i + "].attributeValues")
					);
				}
			} else {
				// 属性商品：校验属性名与 CA 一致
				validateSkuAttributesAgainstCA(attrValues, newCA, i);
			}

			newSku.setAttributeValues(toJsonObject(attrValues));
			productSKUMapper.insert(newSku);
		}
	}

	/**
	 * CA 未改变场景：逐个更新 SKU（旧的作废，创建新的）
	 * @param skuUpdates SKU 更新 DTO 列表
	 * @param currentCA 当前核心属性列表
	 * @param spuId SPU主键ID
	 * @param merchantId 商户ID
	 */
	private void updateSkusByDTOs(List<ProductSkuUpdateDto> skuUpdates, List<String> currentCA,
								  Long spuId, Long merchantId) {
		for (int i = 0; i < skuUpdates.size(); i++) {
			ProductSkuUpdateDto dto = skuUpdates.get(i);
			if (dto == null) {
				throw new ProductUpdateInvalidException("SKU更新项不能为null", Map.of("field", "skus", "index", i));
			}
			if (dto.getId() == null) {
				throw new ProductUpdateInvalidException(
					"CA未改变时，SKU更新必须提供id",
					Map.of("field", "skus[" + i + "].id")
				);
			}

			// 查找旧 SKU
			ProductSKU oldSku = productSKUMapper.selectById(dto.getId());
			if (oldSku == null || !oldSku.getSpuId().equals(spuId)) {
				throw new ProductUpdateInvalidException(
					"SKU不存在或不属于当前SPU",
					Map.of("field", "skus[" + i + "].id", "skuId", dto.getId(), "spuId", spuId)
				);
			}

			// 旧 SKU 作废
			deactivateSkuById(oldSku.getId());

			// 以旧 SKU 为模板，DTO 非 null 字段覆盖，创建新 SKU
			ProductSKU newSku = buildNewSkuFromOld(oldSku, dto, spuId);

			// 校验新 SKU 的属性与当前 CA 一致
			Map<String, Object> attrValues = normalizeAttributeValues(
				objectMapper.convertValue(newSku.getAttributeValues(), ATTRIBUTE_MAP_TYPE)
			);
			if (currentCA.isEmpty()) {
				if (attrValues != null && !attrValues.isEmpty()) {
					throw new ProductUpdateInvalidException(
						"单品SPU的SKU属性必须为空对象{}",
						Map.of("field", "skus[" + i + "].attributeValues")
					);
				}
			} else {
				validateSkuAttributesAgainstCA(attrValues, currentCA, i);
			}

			newSku.setAttributeValues(toJsonObject(attrValues));
			productSKUMapper.insert(newSku);
		}
	}

	/**
	 * 以旧 SKU 为模板，DTO 中非 null 字段覆盖，构建新 SKU 对象（不含 id，准备 insert）
	 * @param oldSku 旧 SKU 模板
	 * @param dto 更新 DTO
	 * @param spuId SPU主键ID
	 * @return 新构建的 SKU 对象
	 */
	private ProductSKU buildNewSkuFromOld(ProductSKU oldSku, ProductSkuUpdateDto dto, Long spuId) {
		ProductSKU newSku = new ProductSKU();
		newSku.setSpuId(spuId);
		newSku.setIsActive(1);

		// attributeValues：DTO 有则覆盖，否则沿用旧值
		if (dto.getAttributeValues() != null) {
			newSku.setAttributeValues(toJsonObject(normalizeAttributeValues(dto.getAttributeValues())));
		} else {
			newSku.setAttributeValues(oldSku.getAttributeValues());
		}

		// status
		newSku.setStatus(dto.getStatus() != null ? normalizeSkuStatus(dto.getStatus()) : oldSku.getStatus());

		// imageUrl
		if (dto.getImageUrl() != null) {
			newSku.setImageUrl(isBlank(dto.getImageUrl()) ? null : dto.getImageUrl().trim());
		} else {
			newSku.setImageUrl(oldSku.getImageUrl());
		}

		// price
		newSku.setPrice(dto.getPrice() != null ? dto.getPrice() : oldSku.getPrice());

		// stock
		newSku.setStock(dto.getStock() != null ? dto.getStock() : oldSku.getStock());

		return newSku;
	}

	/**
	 * 不依赖旧 SKU，根据 DTO 全新构建 SKU 对象（用于 CA 改变且 DTO 无 id 的情况）
	 * @param dto 更新 DTO
	 * @param spuId SPU主键ID
	 * @param index 在列表中的索引（用于异常信息）
	 * @return 新构建的 SKU 对象
	 */
	private ProductSKU buildNewSkuFromDTO(ProductSkuUpdateDto dto, Long spuId, int index) {
		if (dto.getPrice() == null) {
			throw new ProductUpdateInvalidException("新建SKU时价格不能为空",
				Map.of("field", "skus[" + index + "].price"));
		}
		if (dto.getStock() == null) {
			throw new ProductUpdateInvalidException("新建SKU时库存不能为空",
				Map.of("field", "skus[" + index + "].stock"));
		}
		if (dto.getAttributeValues() == null) {
			throw new ProductUpdateInvalidException("新建SKU时属性值不能为空",
				Map.of("field", "skus[" + index + "].attributeValues"));
		}

		ProductSKU newSku = new ProductSKU();
		newSku.setSpuId(spuId);
		newSku.setIsActive(1);
		newSku.setAttributeValues(toJsonObject(normalizeAttributeValues(dto.getAttributeValues())));
		newSku.setStatus(normalizeSkuStatus(dto.getStatus()));
		newSku.setImageUrl(dto.getImageUrl() != null && !isBlank(dto.getImageUrl()) ? dto.getImageUrl().trim() : null);
		newSku.setPrice(dto.getPrice());
		newSku.setStock(dto.getStock());

		return newSku;
	}

	/**
	 * 校验更新场景下单品SPU的SKU约束：只允许恰好一个空属性SKU
	 * @param skuUpdates SKU更新DTO列表
	 */
	private void validateUpdateSingleSkuWithoutAttributes(List<ProductSkuUpdateDto> skuUpdates) {
		if (skuUpdates.size() != 1) {
			throw new ProductUpdateInvalidException(
				"单品SPU必须只包含一个SKU",
				Map.of("field", "skus", "expectedSize", 1, "actualSize", skuUpdates.size())
			);
		}
	}

	/**
	 * 校验 SKU 的 attributeValues 与核心属性列表一致
	 * @param attrValues SKU 的属性值
	 * @param coreAttributes 核心属性列表
	 * @param index SKU 在列表中的索引
	 */
	private void validateSkuAttributesAgainstCA(Map<String, Object> attrValues, List<String> coreAttributes, int index) {
		if (attrValues == null || attrValues.isEmpty()) {
			throw new ProductUpdateInvalidException(
				"属性商品SPU中每个SKU都必须包含attributeValues",
				Map.of("field", "skus[" + index + "].attributeValues")
			);
		}

		Set<String> expectedAttributeNames = new LinkedHashSet<>(coreAttributes);
		Set<String> actualAttributeNames = new LinkedHashSet<>(attrValues.keySet());

		if (actualAttributeNames.contains(null) || actualAttributeNames.stream().anyMatch(String::isBlank)) {
			throw new ProductUpdateInvalidException(
				"SKU属性名不能为空",
				Map.of("field", "skus[" + index + "].attributeValues")
			);
		}

		if (!actualAttributeNames.equals(expectedAttributeNames)) {
			throw new ProductUpdateInvalidException(
				"SKU属性名与SPU核心属性不一致",
				Map.of(
					"field", "skus[" + index + "].attributeValues",
					"expected", expectedAttributeNames,
					"actual", actualAttributeNames
				)
			);
		}

		for (String attributeName : expectedAttributeNames) {
			Object attributeValue = attrValues.get(attributeName);
			if (attributeValue == null) {
				throw new ProductUpdateInvalidException(
					"SKU属性值不能为空",
					Map.of("field", "skus[" + index + "].attributeValues." + attributeName)
				);
			}
			if (attributeValue instanceof CharSequence text && text.toString().trim().isBlank()) {
				throw new ProductUpdateInvalidException(
					"SKU属性值不能为空字符串",
					Map.of("field", "skus[" + index + "].attributeValues." + attributeName)
				);
			}
		}
	}

	/**
	 * 重新计算某 SPU 下所有 active SKU 的最低价格
	 * @param spuId SPU主键ID
	 * @return 最低价格，无 active SKU 时返回 0.00
	 */
	private BigDecimal recalculateMinPrice(Long spuId) {
		List<ProductSKU> activeSkus = productSKUMapper.selectList(
			new LambdaQueryWrapper<ProductSKU>()
				.eq(ProductSKU::getSpuId, spuId)
				.eq(ProductSKU::getIsActive, 1)
				.select(ProductSKU::getPrice)
		);

		return activeSkus.stream()
			.map(ProductSKU::getPrice)
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder())
			.orElse(BigDecimal.ZERO);
	}
}
