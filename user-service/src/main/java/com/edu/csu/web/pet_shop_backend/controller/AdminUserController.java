package com.edu.csu.web.pet_shop_backend.controller;

import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.common.Constant.Result;
import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.AdminJWTContextValidator;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.dto.UserStatusUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserVo;
import com.edu.csu.web.pet_shop_backend.service.AdminUserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private AdminUserService adminUserService;

    @GetMapping
    public Result<PageResult<UserVo>> getUsers(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {

        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        PageResult<UserVo> pageResult = adminUserService.getUsersPage(page, size, userId, username, email, role, status);
        return Result.success(pageResult);
    }

    @GetMapping("/{id}")
    public Result<UserVo> getUserById(@PathVariable Long id,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        UserVo userVo = adminUserService.getUserDetail(id);
        return Result.success(userVo);
    }

    @PutMapping("/{id}/status")
    public Result<?> updateUserStatus(@PathVariable Long id, 
                                      @Valid @RequestBody UserStatusUpdateDto dto,
                                      @InjectJWTUserContext JWTUserContext jwtUserContext) {
        AdminJWTContextValidator.requireAdmin(jwtUserContext);
        adminUserService.updateUserStatus(id, dto.getStatus());
        return Result.success();
    }
}