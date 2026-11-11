package com.edu.csu.web.pet_shop_backend.entity.vo;

import java.util.List;

import lombok.Data;

@Data
public class UserProfileVo {

	private String id;
	private String username;
	private String email;
	private String phone;
	private String intro;
	private String avatar;
	private String joinedAt;
	private String address;
	private List<String> preferences;
}