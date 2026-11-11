package com.edu.csu.web.pet_shop_backend.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductListDto{
    private Integer page = 1;
    private Integer size = 10;
    private String status = "all";
    private List<String> tags = List.of();
    private String keyword = "";
}
