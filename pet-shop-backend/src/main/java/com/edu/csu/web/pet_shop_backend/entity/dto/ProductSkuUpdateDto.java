package com.edu.csu.web.pet_shop_backend.entity.dto;

import java.util.Map;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductSkuUpdateDto {
    private Long id;

    private Map<String, Object> attributeValues;

    private String status;

    private String imageUrl;

    private BigDecimal price;

    private Integer stock;
}
