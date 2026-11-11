package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductSpuVo {
    private Long id;
    private Long merchantId;
    private String name;
    private String description;
    private String imageUrl;
    private String status;
    private String auditReason;
    private BigDecimal minPrice;
    private List<String> coreAttributes; // 前端需要数组格式
    private LocalDateTime createdAt;
}