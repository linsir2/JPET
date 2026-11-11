package com.edu.csu.web.pet_shop_backend.entity.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVo {
    private Long id;
    private String username;
    private String email;
    private String role;
    private String status;
    private String profile;
    private LocalDateTime createdAt;
}
