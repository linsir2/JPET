package com.edu.csu.web.pet_shop_backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.config.ProductUploadProperties;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantProfileUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantProfileVo;
import com.edu.csu.web.pet_shop_backend.service.MerchantProfileService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.Valid;

/**
 * 商家管理界面中的商家信息板块接口。
 */
@RestController
@RequestMapping("/api/merchant/profile")
public class MerchantProfileController {

	private final MerchantProfileService merchantProfileService;
	private final ProductUploadProperties productUploadProperties;

	public MerchantProfileController(MerchantProfileService merchantProfileService,
			ProductUploadProperties productUploadProperties) {
		this.merchantProfileService = merchantProfileService;
		this.productUploadProperties = productUploadProperties;
	}

	@GetMapping
	public Result<MerchantProfileVo> getProfile(@InjectJWTUserContext JWTUserContext jwtUserContext) {
		return Result.success(merchantProfileService.getProfileForCurrentUser(
			jwtUserContext == null ? null : jwtUserContext.getUserId(),
			jwtUserContext == null ? null : jwtUserContext.getUsername()
		));
	}

	@PutMapping
	public Result<MerchantProfileVo> updateProfile(@Valid @RequestBody MerchantProfileUpdateDto request,
			@InjectJWTUserContext JWTUserContext jwtUserContext) {
		return Result.success(merchantProfileService.updateProfileForCurrentUser(
			request,
			jwtUserContext == null ? null : jwtUserContext.getUserId(),
			jwtUserContext == null ? null : jwtUserContext.getUsername()
		));
	}

	@PostMapping("/avatar")
	public Result<MerchantProfileVo> uploadAvatar(@RequestParam("file") MultipartFile file,
			@InjectJWTUserContext JWTUserContext jwtUserContext) throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("头像图片不能为空");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw new IllegalArgumentException("仅支持上传图片文件");
		}

		Path uploadDir = Paths.get(productUploadProperties.getDir(), "merchant-avatar").toAbsolutePath().normalize();
		Files.createDirectories(uploadDir);

		String originalName = file.getOriginalFilename() == null ? "merchant-avatar" : file.getOriginalFilename();
		String ext = "";
		int dot = originalName.lastIndexOf('.');
		if (dot >= 0 && dot < originalName.length() - 1) {
			ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
		}

		String filename = UUID.randomUUID() + ext;
		Path target = uploadDir.resolve(filename).normalize();
		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

		String publicPath = productUploadProperties.getPublicPath().replaceAll("^/+", "").replaceAll("/+$", "");
		String imageUrl = publicPath + "/merchant-avatar/" + filename;
		return Result.success(merchantProfileService.updateAvatarForCurrentUser(
			imageUrl,
			jwtUserContext == null ? null : jwtUserContext.getUserId(),
			jwtUserContext == null ? null : jwtUserContext.getUsername()
		));
	}
}
