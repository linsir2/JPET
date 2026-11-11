package com.edu.csu.web.pet_shop_backend.entity.pojo;

import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JWTUserContext {
    private Long userId;
    private UserRoleEnum role;
    private String username;
}
