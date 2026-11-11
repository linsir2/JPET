package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductSpuUpdateDto {
    @NotNull(message = "商品SPU ID不能为空")
    private Long id;

    private String name;

    private String description;

    private String imageUrl;

    private String status;

    private List<String> coreAttributes;

    /** 商品标签列表（可选），null表示不操作标签，空列表表示清空所有标签 */
    private List<String> tags;

    private List<ProductSkuUpdateDto> skus;
}
