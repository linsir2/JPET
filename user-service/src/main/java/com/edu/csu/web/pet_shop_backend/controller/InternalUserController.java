package com.edu.csu.web.pet_shop_backend.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.dto.InternalUserPatchDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.service.InternalUserService;

/**
 * 内部用户接口，仅供 product-service / order-service 通过 Feign 调用。
 * 由 common 的 InternalTokenFilter 校验 X-Internal-Token。
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

	private final InternalUserService internalUserService;

	public InternalUserController(InternalUserService internalUserService) {
		this.internalUserService = internalUserService;
	}

	@GetMapping("/{id}")
	public Result<UserBriefVo> get(@PathVariable("id") Long id) {
		return Result.success(internalUserService.get(id));
	}

	@PostMapping("/batch")
	public Result<List<UserBriefVo>> batch(@RequestBody List<Long> ids) {
		return Result.success(internalUserService.batch(ids));
	}

	@PutMapping("/{id}")
	public Result<Void> patch(@PathVariable("id") Long id, @RequestBody InternalUserPatchDto dto) {
		internalUserService.patch(id, dto);
		return Result.<Void>success("updated", null);
	}
}
