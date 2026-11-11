package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductSkuCreateDto {
    // 跟随商品 SPU 一同创建时不需要；分开创建时必填
    private Long spuId;

    @NotNull(message = "商品SKU属性值不能为空")
    private Map<String, Object> attributeValues;

    private String status = "available";

    private String imageUrl;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.00", inclusive = false, message = "商品价格必须大于0")
    private BigDecimal price;

    @NotNull(message = "商品库存不能为空")
    @Min(value = 0, message = "商品库存不能小于0")
    private Integer stock;
}
