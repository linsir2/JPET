package com.edu.csu.web.pet_shop_backend.service;

import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantAuthenticationApplyDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.AuthenticationVo;

import java.util.List;

public interface MerchantAuthenticationService {
    void applyOfficialAuthentication(Long userId, MerchantAuthenticationApplyDto dto);
    List<AuthenticationVo> listCurrentMerchantAuthentications(Long userId);

    void deleteAuthentication(Long id, Long userId);
}