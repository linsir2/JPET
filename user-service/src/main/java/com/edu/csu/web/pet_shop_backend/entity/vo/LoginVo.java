package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVo {

    private String accessToken;
    private String refreshToken;

    /**用户角色，方便判断*/
    private String role;
}
