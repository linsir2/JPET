package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Spring MVC 相关能力的配置类。
 * 注意：@InjectJWTUserContext 的参数解析器由 common 的 JwtArgumentResolverConfig 统一注册。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer{

	@Autowired
	private ProductUploadProperties productUploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadDir = Paths.get(productUploadProperties.getDir()).toAbsolutePath().normalize();
        String location = uploadDir.toUri().toString();
        String publicPath = productUploadProperties.getPublicPath().replaceAll("^/+", "").replaceAll("/+$", "");

        registry.addResourceHandler("/spa/index.html")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache().cachePrivate().mustRevalidate());
        registry.addResourceHandler("/spa/assets/**")
            .addResourceLocations("classpath:/static/spa/assets/")
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/spa/images/**", "/spa/favicon.svg", "/spa/icons.svg")
            .addResourceLocations("classpath:/static/spa/images/", "classpath:/static/spa/")
            .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic());

        registry.addResourceHandler("/" + publicPath + "/**")
            .addResourceLocations(location.endsWith("/") ? location : location + "/")
            .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
        registry.addResourceHandler("/spa/" + publicPath + "/**")
            .addResourceLocations(location.endsWith("/") ? location : location + "/")
            .setCacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/spa").setViewName("forward:/spa/index.html");
        registry.addViewController("/spa/").setViewName("forward:/spa/index.html");
        registry.addViewController("/spa/{path:[^\\.]*}").setViewName("forward:/spa/index.html");
        registry.addViewController("/spa/{path:[^\\.]*}/{path2:[^\\.]*}").setViewName("forward:/spa/index.html");
    }
}
