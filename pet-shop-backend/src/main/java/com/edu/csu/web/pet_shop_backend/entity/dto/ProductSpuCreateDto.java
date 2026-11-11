package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductSpuCreateDto {
    
    @NotBlank(message = "商品名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "商品图片不能为空")
    private String imageUrl;

    private String status = "draft";

    private List<String> coreAttributes;

    /** 商品标签列表（可选），null表示不操作标签 */
    private List<String> tags;

    @Valid
    private List<ProductSkuCreateDto> skus;
}
