package com.edu.csu.web.pet_shop_backend.entity.pojo;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseProductSPU {
    private Long id;
    
    private String name;

    private String imageUrl;

    private BigDecimal minPrice;

    /** 商品总销量（只读） */
    private Integer sales;
}
