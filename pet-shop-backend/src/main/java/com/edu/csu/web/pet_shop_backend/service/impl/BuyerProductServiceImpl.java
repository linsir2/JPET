package com.edu.csu.web.pet_shop_backend.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSKU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSpuTagRel;
import com.edu.csu.web.pet_shop_backend.entity.model.Tag;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerProductOptionVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.BuyerProductVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductSkuVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.client.OrderDirectory;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSKUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSpuTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.TagMapper;
import com.edu.csu.web.pet_shop_backend.service.BuyerProductService;

@Service
public class BuyerProductServiceImpl implements BuyerProductService {

	private static final String SPU_STATUS_PUBLISHED = "published";
	private static final String SKU_STATUS_AVAILABLE = "available";
	private static final Set<String> SIZE_KEYS = Set.of("size", "sizes", "规格", "尺码");
	private static final Set<String> GENERIC_TAGS = Set.of(
		"新品", "热销", "推荐", "爆款", "包邮", "特价", "活动", "限时", "正品", "进口", "纯种");
	private static final List<CategoryRule> CATEGORY_RULES = List.of(
		new CategoryRule("宠物食品", List.of("粮", "主粮", "零食", "罐头", "冻干", "猫条", "肉干", "五谷", "营养")),
		new CategoryRule("宠物用品", List.of("饮水", "饮水机", "饮水器", "喂食", "喂食器", "食盆", "窝", "笼", "猫砂", "玩具", "牵引", "服饰", "清洁", "护理", "用品", "航空箱", "外出箱")),
		new CategoryRule("宠物活体", List.of("狗", "犬", "猫", "兔", "鼠", "鹦鹉", "龟", "活体", "宠物兔", "萨摩耶", "布偶", "英短", "金毛")));

	private final ProductSPUMapper productSPUMapper;
	private final ProductSKUMapper productSKUMapper;
	private final ProductSpuTagRelMapper productSpuTagRelMapper;
	private final TagMapper tagMapper;
	private final OrderDirectory orderDirectory;

	public BuyerProductServiceImpl(ProductSPUMapper productSPUMapper,
			ProductSKUMapper productSKUMapper,
			ProductSpuTagRelMapper productSpuTagRelMapper,
			TagMapper tagMapper,
			OrderDirectory orderDirectory) {
		this.productSPUMapper = productSPUMapper;
		this.productSKUMapper = productSKUMapper;
		this.productSpuTagRelMapper = productSpuTagRelMapper;
		this.tagMapper = tagMapper;
		this.orderDirectory = orderDirectory;
	}

	@Override
	public List<BuyerProductVo> listProducts(String category, String keyword) {
		String normalizedCategory = normalizeNullable(category);
		String normalizedKeyword = normalizeNullable(keyword);

		List<ProductSPU> spus = productSPUMapper.selectList(new LambdaQueryWrapper<ProductSPU>()
			.eq(ProductSPU::getIsActive, 1)
			.eq(ProductSPU::getStatus, SPU_STATUS_PUBLISHED)
			.orderByDesc(ProductSPU::getUpdateTime));
		if (spus.isEmpty()) {
			return List.of();
		}

		List<Long> spuIds = spus.stream().map(ProductSPU::getId).toList();
		Map<Long, List<String>> tagMap = loadTagMap(spuIds);
		Map<Long, BigDecimal> ratingMap = loadRatingMap(spuIds);
		return spus.stream()
			.filter(spu -> matchesCategory(spu.getId(), normalizedCategory, tagMap))
			.filter(spu -> matchesKeyword(spu, normalizedKeyword, tagMap))
			.map(spu -> buildProductVo(spu, tagMap.getOrDefault(spu.getId(), List.of()), ratingMap, false))
			.toList();
	}

	@Override
	public BuyerProductVo getProductDetail(Long productId) {
		if (productId == null || productId <= 0) {
			throw BusinessException.notFound("商品不存在");
		}
		ProductSPU spu = productSPUMapper.selectOne(new LambdaQueryWrapper<ProductSPU>()
			.eq(ProductSPU::getId, productId)
			.eq(ProductSPU::getIsActive, 1)
			.eq(ProductSPU::getStatus, SPU_STATUS_PUBLISHED)
			.last("LIMIT 1"));
		if (spu == null) {
			throw BusinessException.notFound("商品不存在或暂未上架");
		}
		List<Long> spuIds = List.of(spu.getId());
		Map<Long, List<String>> tagMap = loadTagMap(spuIds);
		Map<Long, BigDecimal> ratingMap = loadRatingMap(spuIds);
		return buildProductVo(spu, tagMap.getOrDefault(spu.getId(), List.of()), ratingMap, true);
	}

	@Override
	public List<String> listCategories() {
		return listProducts(null, null).stream()
			.map(BuyerProductVo::getCategory)
			.filter(Objects::nonNull)
			.filter(value -> !value.isBlank())
			.distinct()
			.toList();
	}

	@Override
	public List<String> listSuggestions(String keyword) {
		String normalizedKeyword = normalizeNullable(keyword);
		if (normalizedKeyword == null) {
			return List.of();
		}
		String lowered = normalizedKeyword.toLowerCase(Locale.ROOT);
		return listProducts(null, normalizedKeyword).stream()
			.sorted(Comparator
				.comparingInt((BuyerProductVo product) -> suggestionScore(product, lowered))
				.thenComparing(BuyerProductVo::getHeat, Comparator.nullsLast(Comparator.reverseOrder()))
				.thenComparing(BuyerProductVo::getPurchaseCount, Comparator.nullsLast(Comparator.reverseOrder())))
			.map(BuyerProductVo::getName)
			.filter(Objects::nonNull)
			.distinct()
			.limit(24)
			.toList();
	}

	private int suggestionScore(BuyerProductVo product, String loweredKeyword) {
		String name = normalizeNullable(product.getName());
		if (name != null) {
			String loweredName = name.toLowerCase(Locale.ROOT);
			if (loweredName.startsWith(loweredKeyword)) {
				return 0;
			}
			if (loweredName.contains(loweredKeyword)) {
				return 1;
			}
		}

		if (containsIgnoreCase(product.getCategory(), loweredKeyword)
			|| containsIgnoreCase(product.getSubCategory(), loweredKeyword)) {
			return 2;
		}

		if (product.getTags() != null && product.getTags().stream().filter(Objects::nonNull)
				.anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(loweredKeyword))) {
			return 3;
		}

		if (containsIgnoreCase(product.getDescription(), loweredKeyword)) {
			return 4;
		}

		return 5;
	}

	private BuyerProductVo buildProductVo(ProductSPU spu, List<String> tags, Map<Long, BigDecimal> ratingMap, boolean includeOptions) {
		List<ProductSKU> skus = loadAvailableSkus(spu.getId());
		List<String> normalizedTags = normalizeTags(tags);
		Map<String, BuyerProductOptionVo> sizeMap = new LinkedHashMap<>();
		Map<String, BuyerProductOptionVo> specMap = new LinkedHashMap<>();
		List<ProductSkuVo> skuVos = new ArrayList<>();
		int stock = 0;
		for (ProductSKU sku : skus) {
			stock += sku.getStock() == null ? 0 : Math.max(0, sku.getStock());
			Map<String, Object> attributes = toAttributeMap(sku);
			skuVos.add(new ProductSkuVo(
				sku.getId(),
				attributes,
				sku.getStatus(),
				normalizeImage(sku.getImageUrl()),
				sku.getPrice(),
				sku.getStock(),
				sku.getCreateTime(),
				sku.getUpdateTime()));
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				String key = normalizeNullable(entry.getKey());
				String value = normalizeNullable(String.valueOf(entry.getValue()));
				if (key == null || value == null) {
					continue;
				}
				BuyerProductOptionVo option = new BuyerProductOptionVo(key, value, calcDelta(spu.getMinPrice(), sku.getPrice()));
				if (isSizeKey(key)) {
					sizeMap.putIfAbsent(value, option);
				} else {
					specMap.putIfAbsent(value, option);
				}
			}
		}

		CategoryInfo categoryInfo = resolveCategoryInfo(spu, normalizedTags);
		BigDecimal rating = ratingMap.getOrDefault(spu.getId(), BigDecimal.ZERO);
		int sales = defaultInt(spu.getSales());
		return new BuyerProductVo(
			spu.getId(),
			spu.getName(),
			categoryInfo.category(),
			categoryInfo.subCategory(),
			defaultPrice(spu.getMinPrice()),
			sales,
			sales,
			rating,
			stock,
			spu.getDescription() == null ? "" : spu.getDescription(),
			normalizeImage(spu.getImageUrl()),
			normalizedTags,
			includeOptions ? parseCoreAttributes(spu.getCoreAttributes()) : List.of(),
			includeOptions ? new ArrayList<>(sizeMap.values()) : List.of(),
			includeOptions ? new ArrayList<>(specMap.values()) : List.of(),
			includeOptions ? skuVos : List.of()
		);
	}

	private List<ProductSKU> loadAvailableSkus(Long spuId) {
		return productSKUMapper.selectList(new LambdaQueryWrapper<ProductSKU>()
			.eq(ProductSKU::getSpuId, spuId)
			.eq(ProductSKU::getIsActive, 1)
			.eq(ProductSKU::getStatus, SKU_STATUS_AVAILABLE)
			.orderByAsc(ProductSKU::getPrice));
	}

	private Map<Long, List<String>> loadTagMap(List<Long> spuIds) {
		if (spuIds.isEmpty()) {
			return Map.of();
		}
		List<ProductSpuTagRel> relations = productSpuTagRelMapper.selectList(new LambdaQueryWrapper<ProductSpuTagRel>()
			.in(ProductSpuTagRel::getSpuId, spuIds)
			.orderByAsc(ProductSpuTagRel::getId));
		if (relations.isEmpty()) {
			return Map.of();
		}
		List<Long> tagIds = relations.stream().map(ProductSpuTagRel::getTagId).distinct().toList();
		Map<Long, String> tagNameMap = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds)).stream()
			.collect(Collectors.toMap(Tag::getId, Tag::getName, (left, right) -> left, LinkedHashMap::new));
		Map<Long, List<String>> result = new LinkedHashMap<>();
		for (ProductSpuTagRel relation : relations) {
			String tagName = normalizeNullable(tagNameMap.get(relation.getTagId()));
			if (tagName == null) {
				continue;
			}
			List<String> tags = result.computeIfAbsent(relation.getSpuId(), key -> new ArrayList<>());
			if (!tags.contains(tagName)) {
				tags.add(tagName);
			}
		}
		return result;
	}

	private Map<Long, BigDecimal> loadRatingMap(List<Long> spuIds) {
		if (spuIds == null || spuIds.isEmpty()) {
			return Map.of();
		}
		return orderDirectory.ratings(spuIds);
	}

	private boolean matchesCategory(Long spuId, String category, Map<Long, List<String>> tagMap) {
		if (category == null || "全部".equals(category)) {
			return true;
		}
		List<String> tags = tagMap.getOrDefault(spuId, List.of());
		if (tags.stream().anyMatch(category::equals)) {
			return true;
		}
		CategoryInfo categoryInfo = resolveCategoryInfo(null, tags);
		return category.equals(categoryInfo.category()) || category.equals(categoryInfo.subCategory());
	}

	private boolean matchesKeyword(ProductSPU spu, String keyword, Map<Long, List<String>> tagMap) {
		if (keyword == null) {
			return true;
		}
		String lowered = keyword.toLowerCase(Locale.ROOT);
		if (containsIgnoreCase(spu.getName(), lowered) || containsIgnoreCase(spu.getDescription(), lowered)) {
			return true;
		}
		return tagMap.getOrDefault(spu.getId(), List.of()).stream()
			.anyMatch(tag -> containsIgnoreCase(tag, lowered));
	}

	private boolean containsIgnoreCase(String raw, String keyword) {
		return raw != null && raw.toLowerCase(Locale.ROOT).contains(keyword);
	}

	private List<String> normalizeTags(List<String> tags) {
		if (tags == null || tags.isEmpty()) {
			return List.of();
		}
		List<String> normalized = new ArrayList<>();
		for (String tag : tags) {
			String value = normalizeNullable(tag);
			if (value != null && !normalized.contains(value)) {
				normalized.add(value);
			}
		}
		return normalized;
	}

	private CategoryInfo resolveCategoryInfo(ProductSPU spu, List<String> tags) {
		List<String> labels = new ArrayList<>(normalizeTags(tags));
		String name = spu == null ? null : normalizeNullable(spu.getName());
		if (name != null) {
			labels.add(name);
		}

		CategoryRule matchedRule = CATEGORY_RULES.stream()
			.filter(rule -> labels.stream().anyMatch(rule::matches))
			.findFirst()
			.orElse(null);

		String category = matchedRule == null
			? labels.isEmpty() ? "全部" : "其他分类"
			: matchedRule.label();

		String subCategory = normalizeTags(tags).stream()
			.filter(tag -> !GENERIC_TAGS.contains(tag))
			.filter(tag -> matchedRule == null || matchedRule.matches(tag))
			.findFirst()
			.orElseGet(() -> normalizeTags(tags).stream()
				.filter(tag -> !GENERIC_TAGS.contains(tag))
				.findFirst()
				.orElseGet(() -> normalizeTags(tags).stream().findFirst().orElse(category)));

		return new CategoryInfo(category, subCategory == null ? category : subCategory);
	}

	private record CategoryRule(String label, List<String> keywords) {
		private boolean matches(String raw) {
			String normalized = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
			return keywords.stream().anyMatch(keyword -> normalized.contains(keyword.toLowerCase(Locale.ROOT)));
		}
	}

	private record CategoryInfo(String category, String subCategory) {
	}

	private Map<String, Object> toAttributeMap(ProductSKU sku) {
		if (sku.getAttributeValues() == null || !sku.getAttributeValues().isObject()) {
			return Map.of();
		}
		Map<String, Object> result = new LinkedHashMap<>();
		sku.getAttributeValues().fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
		return result;
	}

	private boolean isSizeKey(String key) {
		String lowered = key.toLowerCase(Locale.ROOT);
		return SIZE_KEYS.contains(key) || SIZE_KEYS.contains(lowered);
	}

	private List<String> parseCoreAttributes(com.fasterxml.jackson.databind.JsonNode raw) {
		if (raw == null || raw.isNull() || !raw.isArray()) {
			return List.of();
		}
		List<String> attributes = new ArrayList<>();
		raw.forEach(item -> {
			String value = item == null ? "" : item.asText("").trim();
			if (!value.isEmpty()) {
				attributes.add(value);
			}
		});
		return attributes;
	}

	private String normalizeImage(String raw) {
		if (raw == null || raw.isBlank()) {
			return "";
		}
		return "/" + raw.replaceFirst("^/+", "");
	}

	private BigDecimal calcDelta(BigDecimal basePrice, BigDecimal skuPrice) {
		BigDecimal safeBase = defaultPrice(basePrice);
		BigDecimal safeSku = defaultPrice(skuPrice);
		return safeSku.subtract(safeBase);
	}

	private BigDecimal defaultPrice(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private Integer defaultInt(Integer value) {
		return value == null ? 0 : value;
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}