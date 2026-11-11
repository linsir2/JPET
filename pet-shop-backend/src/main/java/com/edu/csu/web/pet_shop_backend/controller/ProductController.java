package com.edu.csu.web.pet_shop_backend.controller;

import com.edu.csu.web.pet_shop_backend.config.ProductUploadProperties;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSpuCreateDto;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.io.IOException;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.edu.csu.web.pet_shop_backend.common.utils.Result;
import com.edu.csu.web.pet_shop_backend.entity.vo.ProductListVo;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductListDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSkuCreateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSkuUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.ProductSpuUpdateDto;
import com.edu.csu.web.pet_shop_backend.common.annotation.InjectJWTUserContext;
import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.service.ProductService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import com.edu.csu.web.pet_shop_backend.entity.vo.ProductDetailVo;


@RestController
@RequestMapping("api/merchant/products")
public class ProductController {

    private final ProductService productService;
    private final ProductUploadProperties productUploadProperties;

    public ProductController(ProductService productService, ProductUploadProperties productUploadProperties) {
        this.productService = productService;
        this.productUploadProperties = productUploadProperties;
    }
    
    /**
     * 商户端商品列表接口，支持分页、状态过滤、标签过滤和关键词搜索
     * @param productListDTO
     * @param jwtUserContext
     * @return
     */
    @PostMapping
    public Result<ProductListVo> listProduct(
        @RequestBody ProductListDto productListDTO,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        ProductListVo productListVO = productService.listProducts(productListDTO, userId);
        return Result.success(productListVO);
    }


    /**
     * 商户端商品详情接口，返回商品的详细信息，包括基本信息、价格、库存、标签等
     * @param id
     * @param jwtUserContext
     * @return
     */
    @GetMapping("/{id}")
    public Result<ProductDetailVo> getProductDetail(
        @PathVariable Long id,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        ProductDetailVo detailVO = productService.getProductDetail(id, userId);
        return Result.success(detailVO);
    }


    /**
     * 商户端创建商品接口，支持同时创建SPU和SKU，返回创建成功的商品ID
     * @param createDTO
     * @param jwtUserContext
     * @return
     */
    @PostMapping("/spu")
    public Result<Map<String, Object>> createProduct(
        @Valid @RequestBody ProductSpuCreateDto createDTO,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        Long productId = productService.createProduct(createDTO, userId);
        return Result.success(Map.of("productId", productId));
    }

    @PostMapping("/image")
    public Result<Map<String, Object>> uploadProductImage(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("商品图片不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }

        Path uploadDir = Paths.get(productUploadProperties.getDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);

        String originalName = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot >= 0 && dot < originalName.length() - 1) {
            ext = originalName.substring(dot).toLowerCase(Locale.ROOT);
        }
        String filename = UUID.randomUUID() + ext;
        Path target = uploadDir.resolve(filename).normalize();
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);

        String publicPath = productUploadProperties.getPublicPath().replaceAll("^/+", "").replaceAll("/+$", "");
        return Result.success(Map.of("imageUrl", publicPath + "/" + filename));
    }


    /**
     * 商户端创建商品SKU接口，支持为已存在的SPU批量创建SKU
     * @param createDTOList
     * @param jwtUserContext
     * @return
     */
    @PostMapping("/sku")
    public Result<Void> createProductSku(
        @RequestBody List<@Valid ProductSkuCreateDto> createDTOList,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.createProductSkus(createDTOList, userId);
        return Result.success(null);
    }


    /**
     * 商户端更新商品接口，支持更新SPU的基本信息和SKU列表（围绕 coreAttribute 联动更新）
     * @param updateDTO
     * @param jwtUserContext
     * @return
     */
    @PutMapping("/spu")
    public Result<Void> updateProduct(
        @Valid @RequestBody ProductSpuUpdateDto updateDTO,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.updateProduct(updateDTO, userId);
        return Result.success(null);
    }


    /**
     * 商户端更新商品SKU接口
     * @param updateDTOList
     * @param jwtUserContext
     * @return
     */
    @PutMapping("/sku")
    public Result<Void> updateProductSku(
        @RequestBody List<@Valid ProductSkuUpdateDto> updateDTOList,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.updateProductSkus(updateDTOList, userId);
        return Result.success(null);
    }

    /**
     * 商户端删除商品接口，支持批量删除SPU（联动删除关联SKU），实际执行软删除（更新is_active字段为0）
     * @param spuIds
     * @param jwtUserContext
     * @return
     */
    @DeleteMapping("/{spuId}")
    public Result<Void> deleteProductSpu(@PathVariable("spuId") Long spuId,
        @InjectJWTUserContext JWTUserContext jwtUserContext) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.deleteProductSpus(List.of(spuId), userId);
        return Result.success(null);
    }

    @DeleteMapping("/skus/{skuId}")
    public Result<Void> deleteProductSku(@PathVariable("skuId") Long skuId,
        @InjectJWTUserContext JWTUserContext jwtUserContext) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.deleteSingleSku(skuId, userId);
        return Result.success(null);
    }

    @DeleteMapping("/spu")
    public Result<Void> deleteProductSpu(
        @RequestBody List<@Valid @NotNull Long> spuIds,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.deleteProductSpus(spuIds, userId);
        return Result.success(null);
    }

    /**
     * 商户端删除商品SKU接口，支持批量删除SKU，实际执行软删除（更新is_active字段为0）
     * @param skuIds
     * @param jwtUserContext
     * @return
     */
    @DeleteMapping("/sku")
    public Result<Void> deleteProductSku(
        @RequestBody List<@Valid @NotNull Long> skuIds,
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        productService.deleteProductSkus(skuIds, userId);
        return Result.success(null);
    }

    /**
     * 商户端获取所有正在使用的标签接口，返回一个字符串列表，供前端在创建/编辑商品时选择使用
     * @param jwtUserContext
     * @return
     */
    @GetMapping("/tags")
    public Result<List<String>> getTagUsing(
        @InjectJWTUserContext JWTUserContext jwtUserContext
    ) {
        Long userId = jwtUserContext == null ? null : jwtUserContext.getUserId();
        List<String> tags = productService.getUsingTags(userId);
        return Result.success(tags);
    }

}