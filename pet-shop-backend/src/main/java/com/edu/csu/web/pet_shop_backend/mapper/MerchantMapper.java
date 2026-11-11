package com.edu.csu.web.pet_shop_backend.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.csu.web.pet_shop_backend.entity.model.Merchant;

public interface MerchantMapper extends BaseMapper<Merchant> {

	@Select("""
		SELECT id
		FROM merchant_tb
		WHERE user_id = #{userId}
		  AND status = 'active'
		ORDER BY id DESC
		LIMIT 1
		""")
	Long selectMerchantIdByUserId(@Param("userId") Long userId);

	@Select("""
		SELECT authentication_name FROM merchant_authentication_tb
		WHERE merchant_id = #{merchantId}
		  AND status = 'active'
		ORDER BY id
		""")
	List<String> selectAuthenticationNamesByMerchantId(@Param("merchantId") Long merchantId);

	@Select("""
		SELECT t.name FROM tag_tb t
		INNER JOIN merchant_tag_rel_tb r ON r.tag_id = t.id
		WHERE r.merchant_id = #{merchantId}
		ORDER BY r.id
		""")
	List<String> selectTagNamesByMerchantId(@Param("merchantId") Long merchantId);
}
