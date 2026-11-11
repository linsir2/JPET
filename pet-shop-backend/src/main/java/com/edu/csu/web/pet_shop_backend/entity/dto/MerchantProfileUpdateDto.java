package com.edu.csu.web.pet_shop_backend.entity.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 商家信息修改请求。
 * 字段为 null 表示不修改该项；tags 为 null 表示不修改标签，传空列表表示清空全部标签。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MerchantProfileUpdateDto {

	@Size(max = 500, message = "商家图片 URL 长度不能超过 500")
	private String imageUrl;

	@Size(max = 100, message = "商家名称长度不能超过 100 个字符")
	private String merchantName;

	@Email(message = "邮箱格式不正确")
	@Size(max = 100, message = "邮箱长度不能超过 100 个字符")
	private String email;

	@Size(max = 500, message = "商家简介不超过 500 字")
	private String description;

	@Size(max = 30, message = "商家标签数量不能超过 30 个")
	private List<@Size(max = 50, message = "单个商家标签名称长度不能超过 50 个字符") String> tags;
}