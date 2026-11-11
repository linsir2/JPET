package com.edu.csu.web.pet_shop_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantAuthenticationApplyDto;
import com.edu.csu.web.pet_shop_backend.entity.model.MerchantAuthentication;
import com.edu.csu.web.pet_shop_backend.entity.vo.AuthenticationVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.exception.LoginException;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantAuthenticationMapper;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.service.MerchantAuthenticationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class MerchantAuthenticationServiceImpl implements MerchantAuthenticationService {

    private static final String AUTHENTICATION_NAME = "官方旗舰店认证";
    private static final String AUTHENTICATION_STATUS_PENDING = "pending";
    private static final String AUTHENTICATION_STATUS_ACTIVE = "active";

    private final MerchantMapper merchantMapper;
    private final MerchantAuthenticationMapper merchantAuthenticationMapper;

    public MerchantAuthenticationServiceImpl(MerchantMapper merchantMapper,
            MerchantAuthenticationMapper merchantAuthenticationMapper) {
        this.merchantMapper = merchantMapper;
        this.merchantAuthenticationMapper = merchantAuthenticationMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyOfficialAuthentication(Long userId, MerchantAuthenticationApplyDto dto) {
        if (userId == null) {
            throw new LoginException("登录信息已失效，请重新登录", null);
        }
        Long merchantId = merchantMapper.selectMerchantIdByUserId(userId);
        if (merchantId == null) {
            throw BusinessException.badRequest("当前账号尚未完成商家认证，无法申请官方旗舰店认证");
        }

        MerchantAuthentication latest = merchantAuthenticationMapper.selectOne(
            new LambdaQueryWrapper<MerchantAuthentication>()
                .eq(MerchantAuthentication::getMerchantId, merchantId)
                .eq(MerchantAuthentication::getAuthenticationName, AUTHENTICATION_NAME)
                .orderByDesc(MerchantAuthentication::getId)
                .last("LIMIT 1")
        );

        if (latest != null) {
            if (AUTHENTICATION_STATUS_PENDING.equals(latest.getStatus())) {
                throw BusinessException.conflict("官方旗舰店认证申请已提交，请等待管理员审核");
            }
            if (AUTHENTICATION_STATUS_ACTIVE.equals(latest.getStatus())) {
                throw BusinessException.conflict("当前店铺已经完成官方旗舰店认证");
            }
        }

        MerchantAuthentication authentication = new MerchantAuthentication();
        authentication.setMerchantId(merchantId);
        authentication.setAuthenticationName(AUTHENTICATION_NAME);
        authentication.setCompanyName(dto.getCompanyName().trim());
        authentication.setCertificateImageUrl(dto.getCertificateImageUrl().trim());
        authentication.setStatus(AUTHENTICATION_STATUS_PENDING);
        authentication.setAuthLevel("官方旗舰店");
        merchantAuthenticationMapper.insert(authentication);
    }

    @Override
    public List<AuthenticationVo> listCurrentMerchantAuthentications(Long userId) {
        if (userId == null) {
            throw new LoginException("登录信息已失效，请重新登录", null);
        }
        Long merchantId = merchantMapper.selectMerchantIdByUserId(userId);
        if (merchantId == null) {
            throw BusinessException.badRequest("当前账号尚未完成商家认证");
        }

        return merchantAuthenticationMapper.selectList(
            new LambdaQueryWrapper<MerchantAuthentication>()
                .eq(MerchantAuthentication::getMerchantId, merchantId)
                .orderByDesc(MerchantAuthentication::getCreateTime)
        ).stream().map(item -> {
            AuthenticationVo vo = new AuthenticationVo();
            BeanUtils.copyProperties(item, vo);
            vo.setCreatedAt(item.getCreateTime());
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAuthentication(Long id, Long userId) {
        if (userId == null) {
            throw new LoginException("登录信息已失效，请重新登录", null);
        }
        Long merchantId = merchantMapper.selectMerchantIdByUserId(userId);
        if (merchantId == null) {
            throw BusinessException.badRequest("当前账号尚未完成商家认证");
        }
        MerchantAuthentication authentication = merchantAuthenticationMapper.selectById(id);
        if (authentication == null || !Objects.equals(authentication.getMerchantId(), merchantId)) {
            throw BusinessException.notFound("认证申请不存在");
        }
        if (AUTHENTICATION_STATUS_ACTIVE.equals(authentication.getStatus())) {
            throw BusinessException.conflict("已生效的认证不可删除");
        }
        merchantAuthenticationMapper.deleteById(id);
    }
}