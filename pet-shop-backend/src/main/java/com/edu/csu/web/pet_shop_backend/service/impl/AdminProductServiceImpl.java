package com.edu.csu.web.pet_shop_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductAuditDto;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSKU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.edu.csu.web.pet_shop_backend.entity.model.ProductSpuTagRel;
import com.edu.csu.web.pet_shop_backend.entity.model.Tag;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductDetailVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductSkuVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductSpuVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSKUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSpuTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.TagMapper;
import com.edu.csu.web.pet_shop_backend.service.AdminProductService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminProductServiceImpl extends ServiceImpl<ProductSPUMapper, ProductSPU> implements AdminProductService {

    private static final String PRODUCT_STATUS_PENDING = "pending";
    private static final String PRODUCT_STATUS_PUBLISHED = "published";
    private static final String PRODUCT_STATUS_REJECTED = "rejected";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductSKUMapper productSKUMapper;

    @Autowired
    private ProductSpuTagRelMapper productSpuTagRelMapper;

    @Autowired
    private TagMapper tagMapper;

    @Override
    public PageResult<ProductSpuVo> getPendingProductsPage(Integer current, Integer size) {
        Page<ProductSPU> page = new Page<>(current, size);

        LambdaQueryWrapper<ProductSPU> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProductSPU::getStatus, PRODUCT_STATUS_PENDING)
                    .orderByAsc(ProductSPU::getCreateTime);

        this.page(page, queryWrapper);

        List<ProductSpuVo> voList = page.getRecords().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());

        return new PageResult<>(page.getTotal(), voList);
    }

    @Override
    public ProductDetailVo getProductDetail(Long id) {
        ProductSPU productSpu = this.getById(id);
        if (productSpu == null || productSpu.getIsActive() == null || productSpu.getIsActive() != 1) {
            throw BusinessException.notFound("该商品不存在");
        }

        return new ProductDetailVo(
                productSpu.getId(),
                productSpu.getName(),
                productSpu.getDescription(),
                normalizeImage(productSpu.getImageUrl()),
                productSpu.getStatus(),
                productSpu.getAuditReason(),
                loadTags(productSpu.getId()),
                defaultPrice(productSpu.getMinPrice()),
                productSpu.getSales() == null ? 0 : productSpu.getSales(),
                parseCoreAttributes(productSpu.getCoreAttributes()),
                loadSkuVos(productSpu.getId()),
                java.util.List.of(),
                productSpu.getCreateTime(),
                productSpu.getUpdateTime());
    }

    @Override
    public void auditProduct(Long id, ProductAuditDto auditDto) {
        String status = auditDto.getAuditStatus();
        String reason = auditDto.getAuditReason();

        if (PRODUCT_STATUS_REJECTED.equals(status) && (reason == null || reason.trim().isEmpty())) {
            throw new RuntimeException("拒绝商品上架时必须填写拒绝原因");
        }

        if (!PRODUCT_STATUS_PUBLISHED.equals(status) && !PRODUCT_STATUS_REJECTED.equals(status)) {
            throw new RuntimeException("审核状态只能是 published 或 rejected");
        }

        ProductSPU dbProduct = this.getById(id);
        if (dbProduct == null) {
            throw new RuntimeException("该商品不存在");
        }
        if (!PRODUCT_STATUS_PENDING.equals(dbProduct.getStatus())) {
            throw new RuntimeException("操作失败：只能对处于【pending】状态的商品进行审核！");
        }

        ProductSPU productSpuTb = new ProductSPU();
        productSpuTb.setId(id);
        productSpuTb.setStatus(status);
        productSpuTb.setAuditReason(reason);

        boolean success = this.updateById(productSpuTb);
        if (!success) {
            throw new RuntimeException("审核失败，该商品可能不存在");
        }
    }

    private ProductSpuVo convertToVo(ProductSPU productSpuTb) {
        ProductSpuVo vo = new ProductSpuVo();
        BeanUtils.copyProperties(productSpuTb, vo);
        vo.setCreatedAt(productSpuTb.getCreateTime());

        // 解析 JSON 字符串
        if (productSpuTb.getCoreAttributes() != null && !productSpuTb.getCoreAttributes().isEmpty()) {
            try {
                List<String> attributesList = objectMapper.convertValue(
                        productSpuTb.getCoreAttributes(), 
                        new TypeReference<List<String>>() {}
                );
                vo.setCoreAttributes(attributesList);
            } catch (Exception e) {
                vo.setCoreAttributes(null); // 解析失败容错
            }
        }
        return vo;
    }

    private List<String> parseCoreAttributes(JsonNode raw) {
        if (raw == null || raw.isNull() || !raw.isArray()) {
            return List.of();
        }
        List<String> attributes = new ArrayList<>();
        raw.forEach(item -> {
            String value = item == null ? "" : item.asText("").trim();
            if (!value.isEmpty()) {
                attributes.add(value);
            }
        });
        return attributes;
    }

    private List<String> loadTags(Long spuId) {
        List<ProductSpuTagRel> relations = productSpuTagRelMapper.selectList(new LambdaQueryWrapper<ProductSpuTagRel>()
                .eq(ProductSpuTagRel::getSpuId, spuId));
        if (relations.isEmpty()) {
            return List.of();
        }

        List<Long> tagIds = relations.stream().map(ProductSpuTagRel::getTagId).distinct().toList();
        Map<Long, String> tagNameMap = tagMapper.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getId, tagIds)).stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName, (left, right) -> left, LinkedHashMap::new));

        List<String> tags = new ArrayList<>();
        for (ProductSpuTagRel relation : relations) {
            String tagName = tagNameMap.get(relation.getTagId());
            if (tagName != null && !tagName.isBlank()) {
                tags.add(tagName);
            }
        }
        return tags;
    }

    private List<ProductSkuVo> loadSkuVos(Long spuId) {
        return productSKUMapper.selectList(new LambdaQueryWrapper<ProductSKU>()
                        .eq(ProductSKU::getSpuId, spuId)
                        .eq(ProductSKU::getIsActive, 1)
                        .orderByAsc(ProductSKU::getPrice)
                        .orderByAsc(ProductSKU::getId))
                .stream()
                .map(sku -> new ProductSkuVo(
                        sku.getId(),
                        objectMapper.convertValue(sku.getAttributeValues(), new TypeReference<Map<String, Object>>() {}),
                        sku.getStatus(),
                        normalizeImage(sku.getImageUrl()),
                        sku.getPrice(),
                        sku.getStock(),
                        sku.getCreateTime(),
                        sku.getUpdateTime()))
                .toList();
    }

    private String normalizeImage(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return "/" + raw.replaceFirst("^/+", "");
    }

    private BigDecimal defaultPrice(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}