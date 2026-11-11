package com.edu.csu.web.pet_shop_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminAuthenticationAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.model.MerchantAuthentication;
import com.edu.csu.web.pet_shop_backend.entity.vo.AuthenticationVo;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantAuthenticationMapper;
import com.edu.csu.web.pet_shop_backend.service.AdminAuthenticationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminAuthenticationServiceImpl extends ServiceImpl<MerchantAuthenticationMapper, MerchantAuthentication> implements AdminAuthenticationService {

    private static final String AUTHENTICATION_STATUS_PENDING = "pending";
    private static final String AUTHENTICATION_STATUS_ACTIVE = "active";
    private static final String AUTHENTICATION_STATUS_REJECTED = "rejected";

    @Override
    public List<AuthenticationVo> getAuthenticationsByMerchantId(Integer merchantId) {
        
        // 使用 MyBatis-Plus 的 LambdaQueryWrapper 进行条件查询并按时间倒序排列
        LambdaQueryWrapper<MerchantAuthentication> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(MerchantAuthentication::getMerchantId, merchantId.longValue())
                .orderByDesc(MerchantAuthentication::getCreateTime);

        // this.list() 是 IService 提供的自带方法
        List<MerchantAuthentication> authenticationList = this.list(queryWrapper);

        return authenticationList.stream().map(this::convertToVo).collect(Collectors.toList());
    }

    @Override
    public PageResult<AuthenticationVo> getPendingAuthenticationsPage(Integer page, Integer size) {
        Page<MerchantAuthentication> request = new Page<>(page, size);
        LambdaQueryWrapper<MerchantAuthentication> queryWrapper = new LambdaQueryWrapper<MerchantAuthentication>()
            .eq(MerchantAuthentication::getStatus, AUTHENTICATION_STATUS_PENDING)
            .orderByAsc(MerchantAuthentication::getCreateTime);

        this.page(request, queryWrapper);
        List<AuthenticationVo> list = request.getRecords().stream().map(this::convertToVo).collect(Collectors.toList());
        return new PageResult<>(request.getTotal(), list);
    }

    @Override
    public void auditAuthentication(Long id, AdminAuthenticationAuditDto dto) {
        MerchantAuthentication authentication = this.getById(id);
        if (authentication == null) {
            throw new RuntimeException("认证申请不存在");
        }
        if (!AUTHENTICATION_STATUS_PENDING.equals(authentication.getStatus())) {
            throw new RuntimeException("当前认证申请不处于待审核状态");
        }
        if (AUTHENTICATION_STATUS_REJECTED.equals(dto.getAuditStatus())
            && (dto.getAuditReason() == null || dto.getAuditReason().trim().isEmpty())) {
            throw new RuntimeException("驳回认证时必须填写审核意见");
        }

        MerchantAuthentication update = new MerchantAuthentication();
        update.setId(id);
        update.setStatus(dto.getAuditStatus());
        update.setAuditReason(dto.getAuditReason());
        if (AUTHENTICATION_STATUS_ACTIVE.equals(dto.getAuditStatus())
            && (authentication.getAuthLevel() == null || authentication.getAuthLevel().isBlank())) {
            update.setAuthLevel("官方旗舰店");
        }
        this.updateById(update);
    }

    private AuthenticationVo convertToVo(MerchantAuthentication authentication) {
        AuthenticationVo vo = new AuthenticationVo();
        BeanUtils.copyProperties(authentication, vo);
        vo.setCreatedAt(authentication.getCreateTime());
        return vo;
    }
}