package com.edu.csu.web.pet_shop_backend.config;

import java.time.Duration;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.edu.csu.web.pet_shop_backend.mapper.MerchantMapper;
import com.edu.csu.web.pet_shop_backend.client.OrderDirectory;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSKUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSPUMapper;
import com.edu.csu.web.pet_shop_backend.mapper.ProductSpuTagRelMapper;
import com.edu.csu.web.pet_shop_backend.mapper.TagMapper;
import com.edu.csu.web.pet_shop_backend.client.UserDirectory;
import com.edu.csu.web.pet_shop_backend.service.ProductService;
import com.edu.csu.web.pet_shop_backend.service.TagService;
import com.edu.csu.web.pet_shop_backend.service.impl.MerchantCacheServiceImpl;
import com.edu.csu.web.pet_shop_backend.service.impl.ProductServiceImpl;
import com.edu.csu.web.pet_shop_backend.service.impl.TagServiceImpl;
import com.edu.csu.web.pet_shop_backend.service.support.MerchantCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
@EnableCaching
public class ProductConfig {

	@Bean
	public MerchantCacheService merchantCacheService(MerchantMapper merchantMapper) {
		return new MerchantCacheServiceImpl(merchantMapper);
	}

	@Bean
	public TagService tagService(TagMapper tagMapper, ProductSpuTagRelMapper productSpuTagRelMapper) {
		return new TagServiceImpl(tagMapper, productSpuTagRelMapper);
	}

	@Bean
	public ProductService productService(
		ProductSPUMapper productSPUMapper,
		ProductSKUMapper productSKUMapper,
		ProductSpuTagRelMapper productSpuTagRelMapper,
		TagMapper tagMapper,
		OrderDirectory orderDirectory,
		UserDirectory userDirectory,
		MerchantCacheService merchantCacheService,
		ObjectMapper objectMapper,
		TagService tagService
	) {
		return new ProductServiceImpl(
			productSPUMapper,
			productSKUMapper,
			productSpuTagRelMapper,
			tagMapper,
			orderDirectory,
			userDirectory,
			merchantCacheService,
			objectMapper,
			tagService
		);
	}

	@Bean
	public CacheManager cacheManager() {
		CaffeineCacheManager cacheManager = new CaffeineCacheManager("merchantIdByUserId");
		cacheManager.setCaffeine(
			Caffeine.newBuilder()
				.maximumSize(10_000)
				.expireAfterWrite(Duration.ofMinutes(30))
		);
		return cacheManager;
	}
}
