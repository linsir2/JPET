package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "auth.admin-register")
public class AdminRegistrationProperties {

	private String mainAdminKey;
	private String subAdminKey;
}