package com.edu.csu.web.pet_shop_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 基础的 CRUD 和分页已经全部被 BaseMapper 包含，不需要写 XML
}
