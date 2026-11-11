package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

/**
 * 商家管理界面中的商家信息展示对象。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MerchantProfileVo {

	private String imageUrl;

	private String merchantName;

	private String username;

	private String email;

	private String registrationDate;

	private long registrationDurationDays;

	private String registrationDurationText;

	private BigDecimal turnoverFromCompletedOrders;

	private String description;

	private List<String> authentications;

	private List<String> tags;
}