package com.edu.csu.web.pet_shop_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductDetailVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductSpuVo;

public interface AdminProductService extends IService<ProductSPU> {
    PageResult<ProductSpuVo> getPendingProductsPage(Integer page, Integer size);
    ProductDetailVo getProductDetail(Long id);
    void auditProduct(Long id, ProductAuditDto auditDto);
}