package com.edu.csu.web.pet_shop_backend.controller;

import com.edu.csu.web.pet_shop_backend.common.Constant.Result;
import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.config.ProductUploadProperties;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantAuthenticationApplyDto;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.vo.AuthenticationVo;
import com.edu.csu.web.pet_shop_backend.service.MerchantAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/merchant/authentications")
public class MerchantAuthenticationController {

    private final MerchantAuthenticationService merchantAuthenticationService;
    private final ProductUploadProperties productUploadProperties;

    public MerchantAuthenticationController(MerchantAuthenticationService merchantAuthenticationService,
            ProductUploadProperties productUploadProperties) {
        this.merchantAuthenticationService = merchantAuthenticationService;
        this.productUploadProperties = productUploadProperties;
    }

    @PostMapping("/official")
    public Result<?> applyOfficialAuthentication(@Valid @RequestBody MerchantAuthenticationApplyDto dto,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        merchantAuthenticationService.applyOfficialAuthentication(userId, dto);
        return Result.success();
    }

    @GetMapping
    public Result<List<AuthenticationVo>> listCurrentMerchantAuthentications(
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        return Result.success(merchantAuthenticationService.listCurrentMerchantAuthentications(userId));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAuthentication(@PathVariable("id") Long id,
            @InjectJWTUserContext JWTUserContext jwtUserContext) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        merchantAuthenticationService.deleteAuthentication(id, userId);
        return Result.<Void>success(null);
    }

    @PostMapping("/image")
    public Result<Map<String, Object>> uploadAuthenticationImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("认证图片不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }

        Path uploadDir = Paths.get(productUploadProperties.getDir(), "merchant-auth").toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalName = file.getOriginalFilename() == null ? "auth-image" : file.getOriginalFilename();
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
        }

        String filename = UUID.randomUUID() + ext;
        Path target = uploadDir.resolve(filename).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String publicPath = productUploadProperties.getPublicPath().replaceAll("^/+", "").replaceAll("/+$", "");
        return Result.success(Map.of("imageUrl", publicPath + "/merchant-auth/" + filename));
    }
}