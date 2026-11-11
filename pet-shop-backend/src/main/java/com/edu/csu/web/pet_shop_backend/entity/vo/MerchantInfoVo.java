package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MerchantInfoVo {

    private Long merchantId;
    private String merchantName;
    private String imageUrl;
    private LocalDateTime createTime;
    //注册时长
    private long registeredDays;
    private BigDecimal turnover;
    private String description;
    private String status;
    //商家认证列表
    private List<String> authentications;
    //商家标签列表
    private List<String> tags;

}
