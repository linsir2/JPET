package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailVo {
    private Long id;

    private String name;

    private String description;

    private String imageUrl;

    private String status;

    private String auditReason;

    private List<String> tags;

    private BigDecimal minPrice;

    /** 商品总销量（只读） */
    private Integer sales;

    private List<String> coreAttributes;

    private List<ProductSkuVo> skus;

    private List<ProductBuyerReviewVo> buyerReviews;

    private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
