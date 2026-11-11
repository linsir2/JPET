package com.edu.csu.web.pet_shop_backend.entity.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("administrator_tb")
public class Administrator {

    @TableId(value = "id", type = IdType.AUTO)

    private Long id;

    private String account;

    private String password;

    //主管理员or子管理员
    private String role;

    @TableField(fill= FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill= FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;


}
