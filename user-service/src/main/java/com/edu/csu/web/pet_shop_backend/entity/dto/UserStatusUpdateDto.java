package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;;

@Data
public class UserStatusUpdateDto {
    @NotBlank(message = "状态不可为空")
    private String status;
}
