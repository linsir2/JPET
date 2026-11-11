package com.edu.csu.web.pet_shop_backend.controller;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.config.ProductUploadProperties;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantApplyDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.MerchantApplicationStatusVo;
import com.edu.csu.web.pet_shop_backend.service.MerchantApplicationService;

import jakarta.validation.Valid;

/**
 * 商家入驻申请控制器。
 */
@RestController
@RequestMapping("/api/merchant")
public class MerchantApplicationController {

	private final MerchantApplicationService merchantApplicationService;
	private final ProductUploadProperties productUploadProperties;

	public MerchantApplicationController(MerchantApplicationService merchantApplicationService,
			ProductUploadProperties productUploadProperties) {
		this.merchantApplicationService = merchantApplicationService;
		this.productUploadProperties = productUploadProperties;
	}

	/**
	 * 登录后提交商家入驻申请。
	 * @param applyDto 申请参数
	 * @param jwtUserContext 当前登录用户上下文
	 * @return 商家申请结果
	 */
	@PostMapping("/apply")
	public Result<Map<String, Object>> applyMerchant(@Valid @RequestBody MerchantApplyDto applyDto,
			@InjectJWTUserContext JWTUserContext jwtUserContext) {
		Long merchantId = merchantApplicationService.applyMerchant(
			applyDto,
			jwtUserContext == null ? null : jwtUserContext.getUserId()
		);
		return Result.success(
			"商家入驻申请已提交",
			Map.of("merchantId", merchantId, "status", "pending"),
			Map.of()
		);
	}

	@DeleteMapping("/apply")
	public Result<Void> withdrawApplication(@InjectJWTUserContext JWTUserContext jwtUserContext) {
		Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
		merchantApplicationService.withdrawApplication(userId);
		return Result.<Void>success("withdrawn", null);
	}

	@PostMapping("/apply/status")
	public Result<MerchantApplicationStatusVo> getCurrentApplicationStatus(
			@InjectJWTUserContext JWTUserContext jwtUserContext) {
		return Result.success(
			merchantApplicationService.getCurrentApplicationStatus(
				jwtUserContext == null ? null : jwtUserContext.getUserId()
			)
		);
	}

	@PostMapping("/apply/image")
	public Result<Map<String, Object>> uploadMerchantApplyImage(@RequestParam("file") MultipartFile file)
			throws IOException {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("商家申请图片不能为空");
		}
		String contentType = file.getContentType();
		if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
			throw new IllegalArgumentException("仅支持上传图片文件");
		}

		Path uploadDir = Paths.get(productUploadProperties.getDir(), "merchant-apply").toAbsolutePath().normalize();
		Files.createDirectories(uploadDir);

		String originalName = file.getOriginalFilename() == null ? "merchant-apply" : file.getOriginalFilename();
		String ext = "";
		int dot = originalName.lastIndexOf('.');
		if (dot >= 0 && dot < originalName.length() - 1) {
			ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
		}
		String filename = UUID.randomUUID() + ext;
		Path target = uploadDir.resolve(filename).normalize();
		Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

		String publicPath = productUploadProperties.getPublicPath().replaceAll("^/+", "").replaceAll("/+$", "");
		return Result.success(Map.of("imageUrl", publicPath + "/merchant-apply/" + filename));
	}
}