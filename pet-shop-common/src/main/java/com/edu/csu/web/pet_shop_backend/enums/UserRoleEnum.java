package com.edu.csu.web.pet_shop_backend.enums;

public enum UserRoleEnum {

    // 普通用户
    NORMAL_USER(1, "普通用户", "normal_user"),

    // 商家用户
    MERCHANT_USER(2, "商家用户", "merchant_user"),

    // 主管理员
    MAIN_ADMIN(3, "主管理员", "main_admin"),

    // 子管理员
    SUB_ADMIN(4, "子管理员", "sub_admin");
    
    
    private final int code;
    private final String description;
    private final String value;

    UserRoleEnum(int code, String description, String value) {
        this.code = code;
        this.description = description;
        this.value = value;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return value;
    }

    public static UserRoleEnum fromCode(int code) {
        for (UserRoleEnum roleEnum : values()) {
            if (roleEnum.code == code) {
                return roleEnum;
            }
        }
        throw new IllegalArgumentException("未知的用户角色 code: " + code);
    }

    public static UserRoleEnum fromValue(String value) {
        for (UserRoleEnum roleEnum : values()) {
            if (roleEnum.value.equals(value)) {
                return roleEnum;
            }
        }
        throw new IllegalArgumentException("未知的用户角色 value: " + value);
    }

}
