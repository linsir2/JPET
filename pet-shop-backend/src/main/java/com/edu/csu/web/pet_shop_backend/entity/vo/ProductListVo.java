package com.edu.csu.web.pet_shop_backend.entity.vo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

import com.edu.csu.web.pet_shop_backend.entity.pojo.BaseProductSPU;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductListVo {
    private List<BaseProductSPU> products;
    private int pageTotal;
}