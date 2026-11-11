package com.edu.csu.web.pet_shop_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.model.Merchant;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantVo;

public interface AdminMerchantService extends IService<Merchant> {
    PageResult<MerchantVo> getPendingMerchantsPage(Integer page, Integer size);
    void auditMerchant(Long id, MerchantAuditDto auditDto);
}
