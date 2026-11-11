package com.edu.csu.web.pet_shop_backend.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;

public interface ProductSPUMapper extends BaseMapper<ProductSPU> {

	@Update("""
		UPDATE product_spu_tb
		SET sales = sales + #{qty}
		WHERE id = #{spuId}
		""")
	int incrementSales(@Param("spuId") Long spuId, @Param("qty") int qty);

	@Update("""
		UPDATE product_spu_tb
		SET sales = GREATEST(CAST(sales AS SIGNED) - #{qty}, 0)
		WHERE id = #{spuId}
		""")
	int decrementSales(@Param("spuId") Long spuId, @Param("qty") int qty);
}
