package com.edu.csu.web.pet_shop_backend.controller;

import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.common.Constant.Result;
import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.AdminJWTContextValidator;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantVo;
import com.edu.csu.web.pet_shop_backend.service.AdminMerchantService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/merchants")
public class AdminMerchantController {

    @Autowired
    private AdminMerchantService adminMerchantService;

    @GetMapping("/audits")
    public Result<PageResult<MerchantVo>> getPendingMerchants(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        PageResult<MerchantVo> pageResult = adminMerchantService.getPendingMerchantsPage(page, size);
        return Result.success(pageResult);
    }

    @PutMapping("/{id}/audit")
    public Result<?> auditMerchant(@PathVariable Long id, 
                                   @Valid @RequestBody MerchantAuditDto auditDto,
                                   @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        adminMerchantService.auditMerchant(id, auditDto);
        return Result.success();
    }
}