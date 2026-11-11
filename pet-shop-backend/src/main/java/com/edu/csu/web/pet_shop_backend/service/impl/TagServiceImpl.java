package com.edu.csu.web.pet_shop_backend.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSpuTagRel;
import com.edu.csu.web.pet_shop_backend.entity.model.Tag;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSpuTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.TagMapper;
import com.edu.csu.web.pet_shop_backend.service.TagService;

/**
 * 标签服务实现，负责标签的创建与关联同步
 */
public class TagServiceImpl implements TagService {

	private final TagMapper tagMapper;
	private final ProductSpuTagRelMapper productSpuTagRelMapper;

	public TagServiceImpl(TagMapper tagMapper, ProductSpuTagRelMapper productSpuTagRelMapper) {
		this.tagMapper = tagMapper;
		this.productSpuTagRelMapper = productSpuTagRelMapper;
	}

	@Override
	public Map<String, Long> ensureTagsExist(List<String> tagNames) {
		if (tagNames == null || tagNames.isEmpty()) {
			return Map.of();
		}

		// 去重并过滤空白标签
		List<String> distinctNames = tagNames.stream()
			.map(String::trim)
			.filter(name -> !name.isEmpty())
			.distinct()
			.toList();

		if (distinctNames.isEmpty()) {
			return Map.of();
		}

		// 批量查询已存在的标签
		List<Tag> existingTags = tagMapper.selectList(
			new LambdaQueryWrapper<Tag>().in(Tag::getName, distinctNames)
		);

		Map<String, Long> resultMap = new LinkedHashMap<>();
		for (Tag tag : existingTags) {
			resultMap.put(tag.getName(), tag.getId());
		}

		// 对不存在的标签逐个创建
		for (String name : distinctNames) {
			if (!resultMap.containsKey(name)) {
				Tag newTag = new Tag();
				newTag.setName(name);
				tagMapper.insert(newTag);
				resultMap.put(name, newTag.getId());
			}
		}

		return resultMap;
	}

	@Override
	public void syncSpuTags(Long spuId, List<String> tags) {
		// 查询当前 SPU 的所有标签关联
		List<ProductSpuTagRel> existingRels = productSpuTagRelMapper.selectList(
			new LambdaQueryWrapper<ProductSpuTagRel>().eq(ProductSpuTagRel::getSpuId, spuId)
		);

		// 空列表：清空所有关联
		if (tags.isEmpty()) {
			for (ProductSpuTagRel rel : existingRels) {
				productSpuTagRelMapper.deleteById(rel.getId());
			}
			return;
		}

		// 确保所有目标标签在 tag_tb 中存在
		Map<String, Long> tagMap = ensureTagsExist(tags);
		Set<Long> desiredTagIds = new LinkedHashSet<>(tagMap.values());

		// 计算现有关联的 tagId 集合
		Map<Long, Long> existingTagIdToRelId = new LinkedHashMap<>();
		for (ProductSpuTagRel rel : existingRels) {
			existingTagIdToRelId.put(rel.getTagId(), rel.getId());
		}
		Set<Long> existingTagIds = existingTagIdToRelId.keySet();

		// 计算需要新增的关联（目标中有但现有中没有）
		List<Long> toAdd = new ArrayList<>();
		for (Long tagId : desiredTagIds) {
			if (!existingTagIds.contains(tagId)) {
				toAdd.add(tagId);
			}
		}

		// 计算需要删除的关联（现有中有但目标中没有）
		List<Long> toRemoveRelIds = new ArrayList<>();
		for (Long existingTagId : existingTagIds) {
			if (!desiredTagIds.contains(existingTagId)) {
				toRemoveRelIds.add(existingTagIdToRelId.get(existingTagId));
			}
		}

		// 执行新增
		for (Long tagId : toAdd) {
			ProductSpuTagRel rel = new ProductSpuTagRel();
			rel.setSpuId(spuId);
			rel.setTagId(tagId);
			productSpuTagRelMapper.insert(rel);
		}

		// 执行删除
		for (Long relId : toRemoveRelIds) {
			productSpuTagRelMapper.deleteById(relId);
		}
	}

	@Override
	public List<String> getTagNamesBySpuIds(List<Long> spuIds) {
		if (spuIds == null || spuIds.isEmpty()) {
			return List.of();
		}

		// 查询这些 SPU 关联的所有 tagId（自动去重）
		List<ProductSpuTagRel> rels = productSpuTagRelMapper.selectList(
			new LambdaQueryWrapper<ProductSpuTagRel>().in(ProductSpuTagRel::getSpuId, spuIds)
		);
		if (rels.isEmpty()) {
			return List.of();
		}

		List<Long> tagIds = rels.stream()
			.map(ProductSpuTagRel::getTagId)
			.distinct()
			.toList();

		// 查询标签名称
		List<Tag> tags = tagMapper.selectList(
			new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds)
		);
		return tags.stream()
			.map(Tag::getName)
			.filter(name -> name != null && !name.isEmpty())
			.distinct()
			.toList();
	}
}
