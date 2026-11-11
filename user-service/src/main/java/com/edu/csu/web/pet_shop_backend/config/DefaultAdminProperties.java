package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

@Data
@Component
@ConfigurationProperties(prefix = "auth.default-admin")
public class DefaultAdminProperties {

	private boolean enabled = true;
	private String account;
	private String password;
	private String role;
}