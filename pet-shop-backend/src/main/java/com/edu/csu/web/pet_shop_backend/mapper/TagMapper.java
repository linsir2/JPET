package com.edu.csu.web.pet_shop_backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.edu.csu.web.pet_shop_backend.entity.model.Tag;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {
}
