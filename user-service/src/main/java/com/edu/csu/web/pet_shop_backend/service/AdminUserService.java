package com.edu.csu.web.pet_shop_backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserVo;

public interface AdminUserService extends IService<User>{
    PageResult<UserVo> getUsersPage(Integer page, Integer size, Integer userId,
        String username, String email, String role, String status);

    UserVo getUserDetail(Long id);
    
    void updateUserStatus(Long id, String status);
}
