package com.edu.csu.web.pet_shop_backend.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.edu.csu.web.pet_shop_backend.client.dto.UserPatchDto;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;

@FeignClient(name = "user-service", path = "/internal/users")
public interface UserClient {

	@GetMapping("/{id}")
	Result<UserBriefVo> get(@PathVariable("id") Long id);

	@PostMapping("/batch")
	Result<List<UserBriefVo>> batch(@RequestBody List<Long> ids);

	@PutMapping("/{id}")
	Result<Void> patch(@PathVariable("id") Long id, @RequestBody UserPatchDto dto);
}
