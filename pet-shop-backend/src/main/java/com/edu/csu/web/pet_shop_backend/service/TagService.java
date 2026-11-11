package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;
import java.util.Map;

/**
 * 标签服务接口，负责标签的创建与关联同步，可被商品、商家等模块复用
 */
public interface TagService {

	/**
	 * 批量确保标签在 tag_tb 中存在（不存在则创建），返回 tagName → tagId 的映射
	 * @param tagNames 标签名称列表
	 * @return tagName → tagId 映射
	 */
	Map<String, Long> ensureTagsExist(List<String> tagNames);

	/**
	 * 同步商品SPU的标签关联关系，以传入的 tags 为最终状态：
	 * - null: 不操作（调用方应自行判断，不应传 null 进来）
	 * - 空列表: 清空该 SPU 的所有标签关联
	 * - 非空列表: 新增缺失的关联，删除多余的关联
	 * @param spuId 商品SPU ID
	 * @param tags 目标标签名称列表
	 */
	void syncSpuTags(Long spuId, List<String> tags);

	/**
	 * 根据 SPU ID 列表查询关联的所有标签名称（自动去重）
	 * @param spuIds SPU ID 列表
	 * @return 去重后的标签名称列表
	 */
	List<String> getTagNamesBySpuIds(List<Long> spuIds);
}
