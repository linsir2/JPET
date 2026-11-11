package com.edu.csu.web.pet_shop_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminAuthenticationAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.model.MerchantAuthentication;
import com.edu.csu.web.pet_shop_backend.entity.vo.AuthenticationVo;

import java.util.List;

public interface AdminAuthenticationService extends IService<MerchantAuthentication> {
    List<AuthenticationVo> getAuthenticationsByMerchantId(Integer merchantId);
    PageResult<AuthenticationVo> getPendingAuthenticationsPage(Integer page, Integer size);
    void auditAuthentication(Long id, AdminAuthenticationAuditDto dto);
}
