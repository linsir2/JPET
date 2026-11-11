package com.edu.csu.web.pet_shop_backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@MapperScan("com.edu.csu.web.pet_shop_backend.mapper")
@EnableFeignClients(basePackages = "com.edu.csu.web.pet_shop_backend.client")
public class PetShopBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(PetShopBackendApplication.class, args);
	}

}
