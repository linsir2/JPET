package com.edu.csu.web.pet_shop_backend.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.edu.csu.web.pet_shop_backend.entity.model.ProductSPU;
import com.fasterxml.jackson.databind.JsonNode;

@SpringBootTest
class ProductSPUMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private ProductSPUMapper productSPUMapper;

    @Test
    @Transactional
    void insertAndSelectByIdShouldWork() throws Exception {
        assertNotNull(productSPUMapper);

        ProductSPU productSPU = new ProductSPU();
        productSPU.setMerchantId(1L);
        productSPU.setName("测试商品SPU");
        productSPU.setDescription("用于验证 Mapper 是否可用");
        productSPU.setImageUrl("https://example.com/spu.png");
        productSPU.setStatus("draft");
        productSPU.setAuditReason(null);
        productSPU.setMinPrice(new BigDecimal("19.90"));
        productSPU.setCoreAttributes(objectMapper.readTree("[\"优质等级\",\"重量\"]"));

        int insertedRows = productSPUMapper.insert(productSPU);
        assertEquals(1, insertedRows);
        assertNotNull(productSPU.getId());

        ProductSPU dbProductSPU = productSPUMapper.selectById(productSPU.getId());
        assertNotNull(dbProductSPU);
        assertEquals(productSPU.getMerchantId(), dbProductSPU.getMerchantId());
        assertEquals(productSPU.getName(), dbProductSPU.getName());
        assertEquals(productSPU.getDescription(), dbProductSPU.getDescription());
        assertEquals(productSPU.getImageUrl(), dbProductSPU.getImageUrl());
        assertEquals(productSPU.getStatus(), dbProductSPU.getStatus());
        assertEquals(productSPU.getMinPrice(), dbProductSPU.getMinPrice());
        assertEquals(normalizeJson(productSPU.getCoreAttributes()), normalizeJson(dbProductSPU.getCoreAttributes()));
        assertTrue(dbProductSPU.getId() > 0);
    }

    private String normalizeJson(JsonNode json) {
        return json == null ? null : json.toString().replaceAll("\\s+", "");
    }
}