package com.edu.csu.web.pet_shop_backend.service;

import com.edu.csu.web.pet_shop_backend.entity.dto.UserProfileUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserProfileVo;

public interface UserProfileService {

	UserProfileVo getProfileForCurrentUser(Long userId);

	UserProfileVo updateProfileForCurrentUser(UserProfileUpdateDto request, Long userId);
}