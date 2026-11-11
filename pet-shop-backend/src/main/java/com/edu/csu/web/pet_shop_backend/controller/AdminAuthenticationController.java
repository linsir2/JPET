package com.edu.csu.web.pet_shop_backend.controller;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.common.Constant.Result;
import com.edu.csu.web.pet_shop_backend.common.utils.AdminJWTContextValidator;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminAuthenticationAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.AuthenticationVo;
import com.edu.csu.web.pet_shop_backend.service.AdminAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/merchants")
public class AdminAuthenticationController {

    @Autowired
    private AdminAuthenticationService authenticationService;

    @GetMapping("/authentications/audits")
    public Result<PageResult<AuthenticationVo>> getPendingAuthentications(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        return Result.success(authenticationService.getPendingAuthenticationsPage(page, size));
    }

    @GetMapping("/{merchantId}/authentications")
    public Result<List<AuthenticationVo>> getAuthentications(@PathVariable Integer merchantId,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        List<AuthenticationVo> list = authenticationService.getAuthenticationsByMerchantId(merchantId);
        return Result.success(list);
    }

    @PutMapping("/authentications/{id}/audit")
    public Result<?> auditAuthentication(@PathVariable Long id,
            @Valid @RequestBody AdminAuthenticationAuditDto dto,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        authenticationService.auditAuthentication(id, dto);
        return Result.success();
    }
}