package com.edu.csu.web.pet_shop_backend.service;

import java.util.List;

import com.edu.csu.web.pet_shop_backend.entity.dto.InternalUserPatchDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;

public interface InternalUserService {

	UserBriefVo get(Long id);

	List<UserBriefVo> batch(List<Long> ids);

	void patch(Long id, InternalUserPatchDto dto);
}
