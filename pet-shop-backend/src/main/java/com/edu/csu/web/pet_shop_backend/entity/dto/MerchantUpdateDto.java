package com.edu.csu.web.pet_shop_backend.entity.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MerchantUpdateDto {

    private String merchantName;

    private String imageUrl;

    @Size(max=500,message="商家简介不超过500字")
    private String description;

    /**传null表示不修改，传空列表表示清空标签*/
    private List<String> tags;
}
