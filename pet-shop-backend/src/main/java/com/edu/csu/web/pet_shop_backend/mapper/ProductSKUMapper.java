package com.edu.csu.web.pet_shop_backend.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSKU;

public interface ProductSKUMapper extends BaseMapper<ProductSKU> {

	@Update("""
		UPDATE product_sku_tb
		SET stock = stock - #{qty}
		WHERE id = #{skuId} AND stock >= #{qty}
		""")
	int deductStock(@Param("skuId") Long skuId, @Param("qty") int qty);

	@Update("""
		UPDATE product_sku_tb
		SET stock = stock + #{qty}
		WHERE id = #{skuId}
		""")
	int restoreStock(@Param("skuId") Long skuId, @Param("qty") int qty);
}
