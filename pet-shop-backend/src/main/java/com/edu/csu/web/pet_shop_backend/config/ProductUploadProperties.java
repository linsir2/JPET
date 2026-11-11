package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "product.upload")
public class ProductUploadProperties {

	/**
	 * 文件实际保存目录。
	 */
	private String dir = "uploads/products";

	/**
	 * 对外暴露的访问前缀，对应前端 getImagePath 下的 /spa/uploads/...。
	 */
	private String publicPath = "uploads/products";
}