package com.edu.csu.web.pet_shop_backend.service;

import com.edu.csu.web.pet_shop_backend.entity.dto.ProductListDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSkuCreateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSpuCreateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSkuUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSpuUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductDetailVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductListVo;

import java.util.List;

public interface ProductService {

	ProductListVo listProducts(ProductListDto productListDTO, Long userId);

	ProductDetailVo getProductDetail(Long productId, Long userId);

	Long createProduct(ProductSpuCreateDto createDTO, Long userId);

	void createProductSkus(List<ProductSkuCreateDto> createDTOList, Long userId);

	void updateProduct(ProductSpuUpdateDto updateDTO, Long userId);

	void updateProductSkus(List<ProductSkuUpdateDto> updateDTOList, Long userId);

	void deleteProductSpus(List<Long> spuIds, Long userId);

	void deleteProductSkus(List<Long> skuIds, Long userId);

	void deleteSingleSku(Long skuId, Long userId);

	List<String> getUsingTags(Long userId);
}
