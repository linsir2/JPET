package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.Data;

/**
 * 内部用户快照，供 product-service / order-service 通过 Feign 读取。
 */
@Data
public class UserBriefVo {

	private Long id;

	private String username;

	private String email;

	private String role;

	private String status;

	private String profile;
}
