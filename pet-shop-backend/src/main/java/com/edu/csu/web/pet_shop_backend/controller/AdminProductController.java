package com.edu.csu.web.pet_shop_backend.controller;

import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.common.Constant.Result;
import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.AdminJWTContextValidator;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductDetailVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductSpuVo;
import com.edu.csu.web.pet_shop_backend.service.AdminProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    @Autowired
    private AdminProductService adminProductService;

    @GetMapping("/audits")
    public Result<PageResult<ProductSpuVo>> getPendingProducts(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        PageResult<ProductSpuVo> pageResult = adminProductService.getPendingProductsPage(page, size);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<ProductDetailVo> getProductDetail(@PathVariable Long id,
                                                    @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        return Result.success(adminProductService.getProductDetail(id));
    }

    @PutMapping("/{id}/audit")
    public Result<?> auditProduct(@PathVariable Long id, 
                                  @Valid @RequestBody ProductAuditDto auditDto,
                                  @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        adminProductService.auditProduct(id, auditDto);
        return Result.success();
    }
}