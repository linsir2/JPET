package com.edu.csu.web.pet_shop_backend.service.impl;

import java.util.Objects;

import org.springframework.cache.annotation.Cacheable;

import com.edu.csu.web.pet_shop_backend.exception.MerchantNotFoundException;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.service.support.MerchantCacheService;

public class MerchantCacheServiceImpl implements MerchantCacheService {

    private final MerchantMapper merchantMapper;

    public MerchantCacheServiceImpl(MerchantMapper merchantMapper) {
        this.merchantMapper = merchantMapper;
    }

    @Override
    @Cacheable(cacheNames = "merchantIdByUserId", key = "#userId")
    public Long getMerchantIdByUserId(Long userId) {
        Long merchantId = merchantMapper.selectMerchantIdByUserId(userId);
        if (Objects.isNull(merchantId)) {
            throw new MerchantNotFoundException(userId);
        }
        return merchantId;
    }
}
