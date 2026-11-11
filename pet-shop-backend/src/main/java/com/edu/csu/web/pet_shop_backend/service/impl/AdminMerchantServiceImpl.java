package com.edu.csu.web.pet_shop_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.csu.web.pet_shop_backend.client.UserDirectory;
import com.edu.csu.web.pet_shop_backend.client.dto.UserPatchDto;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.model.Merchant;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.service.AdminMerchantService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AdminMerchantServiceImpl extends ServiceImpl<MerchantMapper, Merchant> implements AdminMerchantService {

    private static final String MERCHANT_STATUS_PENDING = "pending";
    private static final String MERCHANT_STATUS_ACTIVE = "active";
    private static final String MERCHANT_STATUS_REJECTED = "rejected";
    private static final Pattern LEGACY_REGISTERED_MERCHANT_NAME = Pattern.compile(".+_merchant_\\d+$");

    @Autowired
    private UserDirectory userDirectory;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<MerchantVo> getPendingMerchantsPage(Integer current, Integer size) {
        Page<Merchant> page = new Page<>(current, size);

        LambdaQueryWrapper<Merchant> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Merchant::getStatus, MERCHANT_STATUS_PENDING)
                    .orderByAsc(Merchant::getCreateTime);

        this.page(page, queryWrapper);

        List<MerchantVo> voList = page.getRecords().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditMerchant(Long id, MerchantAuditDto auditDto) {
        String status = auditDto.getAuditStatus();
        String reason = auditDto.getAuditReason();

        if (MERCHANT_STATUS_REJECTED.equals(status) && (reason == null || reason.trim().isEmpty())) {
            throw BusinessException.badRequest("拒绝审核时必须填写拒绝理由");
        }

        Merchant dbMerchant = this.getById(id);
        if (dbMerchant == null) {
            throw BusinessException.notFound("该商家不存在");
        }
        if (!MERCHANT_STATUS_PENDING.equals(dbMerchant.getStatus())) {
            throw BusinessException.conflict("操作失败：只能对处于【pending】状态的商家进行审核！");
        }

        // 1. 更新商家状态
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setStatus(status);
        merchant.setAuditReason(reason);
        boolean success = this.updateById(merchant);

        if (!success) {
            throw BusinessException.conflict("审核失败，该商家可能不存在");
        }

        // 2. 审核通过：通过 user-service 的条件 PATCH 同步角色
        if (MERCHANT_STATUS_ACTIVE.equals(status) && dbMerchant.getUserId() != null) {
            userDirectory.patch(dbMerchant.getUserId(), UserPatchDto.role("merchant_user"));
        }
    }

    private MerchantVo convertToVo(Merchant merchant) {
        MerchantVo vo = new MerchantVo();
        BeanUtils.copyProperties(merchant, vo);
        vo.setCreatedAt(merchant.getCreateTime());
        if (merchant.getUserId() != null) {
            UserBriefVo user = userDirectory.get(merchant.getUserId());
            if (user != null) {
                vo.setUserId(user.getId());
                vo.setUsername(user.getUsername());
                vo.setEmail(user.getEmail());
                vo.setPhone(extractPhone(user.getProfile()));
                if (merchant.getMerchantName() == null || LEGACY_REGISTERED_MERCHANT_NAME.matcher(merchant.getMerchantName()).matches()) {
                    vo.setMerchantName(user.getUsername());
                }
            }
        }
        return vo;
    }

    private String extractPhone(String rawProfile) {
        if (rawProfile == null || rawProfile.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawProfile);
            JsonNode phone = root.get("phone");
            if (phone == null || phone.isNull()) {
                return null;
            }
            String value = phone.asText("").trim();
            return value.isEmpty() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }
}
