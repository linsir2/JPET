package com.edu.csu.web.pet_shop_backend.enums;

/**
 * SPU状态枚举类
 * 包含以下状态：
 * - DRAFT: 草稿状态，商户正在编辑但未提交审核
 * - WAITING_REVIEW: 待审核状态，商户已提交审核，等待管理员审核
 * - PUBLISHED: 已发布状态，管理员审核通过，商品已上架
 * - REJECTED: 审核拒绝状态，管理员审核未通过，商户需要修改后重新提交审核
 * - TAKEN_DOWN: 已下架状态，管理员将已发布的商品下架，商户需要修改后重新提交审核才能再次上架
 */
public enum ProductSPUStatusEnum {
    DRAFT("draft", "草稿"),
    PENDING("pending", "待审核"),
    PUBLISHED("published", "已发布"),
    REJECTED("rejected", "已拒绝"),
    TAKEN_DOWN("taken_down", "已下架");

    private final String value;
    private final String description;

    ProductSPUStatusEnum(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }
}
