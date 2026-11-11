package com.edu.csu.web.pet_shop_backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.edu.csu.web.pet_shop_backend.common.Constant.PageResult;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserVo;
import com.edu.csu.web.pet_shop_backend.mapper.UserMapper;
import com.edu.csu.web.pet_shop_backend.service.AdminUserService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminUserServiceImpl extends ServiceImpl<UserMapper, User> implements AdminUserService{
    @Override
    public PageResult<UserVo> getUsersPage(Integer current, Integer size, Integer userId,
        String username, String email, String role, String status
    ) {
        // 构造mybatis-plus分页对象
        Page<User> page = new Page<>(current, size);
        // 构造查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();

        queryWrapper.eq(userId != null, User::getId, userId)
                .like(username != null && !username.isEmpty(), User::getUsername, username)
                .eq(email != null && !email.isEmpty(), User::getEmail, email)
                .eq(role != null && !role.isEmpty(), User::getRole, role)
                .eq(status != null && !status.isEmpty(), User::getStatus, status)
                .orderByDesc(User::getCreateTime); // 默认根据注册时间倒序

        if (status == null || status.isBlank()) {
            queryWrapper.ne(User::getStatus, "deleted");
        }
        
        // 分页查询
        this.page(page, queryWrapper);
        // 转为VO
        List<UserVo> voList = page.getRecords().stream()
                .map(this::convertToVo)
                .collect(Collectors.toList());
        
        return new PageResult<>(page.getTotal(), voList);
    }

    private UserVo convertToVo(User user) {
        UserVo vo = new UserVo();
        BeanUtils.copyProperties(user, vo);
        vo.setCreatedAt(user.getCreateTime());
        return vo;
    }

    @Override
    public UserVo getUserDetail(Long id) {
        User user = this.getById(id); // MyBatis-Plus 自带方法
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return convertToVo(user);
    }

    @Override
    public void updateUserStatus(Long id, String status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        
        // MyBatis-Plus 自带方法，根据 ID 更新非空字段
        boolean success = this.updateById(user); 
        if (!success) {
            throw new RuntimeException("状态更新失败，用户可能不存在");
        }
    }
}
