package com.edu.csu.web.pet_shop_backend.service.impl;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.dto.InternalUserPatchDto;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserBriefVo;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.mapper.UserMapper;
import com.edu.csu.web.pet_shop_backend.service.InternalUserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class InternalUserServiceImpl implements InternalUserService {

	private static final String STATUS_NORMAL = "normal";

	private final UserMapper userMapper;
	private final ObjectMapper objectMapper;

	public InternalUserServiceImpl(UserMapper userMapper, ObjectMapper objectMapper) {
		this.userMapper = userMapper;
		this.objectMapper = objectMapper;
	}

	@Override
	public UserBriefVo get(Long id) {
		if (id == null) {
			return null;
		}
		return toBrief(userMapper.selectById(id));
	}

	@Override
	public List<UserBriefVo> batch(List<Long> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) {
			return List.of();
		}
		return userMapper.selectBatchIds(distinctIds).stream()
			.map(this::toBrief)
			.filter(Objects::nonNull)
			.toList();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void patch(Long id, InternalUserPatchDto dto) {
		if (dto == null) {
			return;
		}
		User user = userMapper.selectById(id);
		if (user == null) {
			throw BusinessException.notFound("用户不存在");
		}

		if (dto.getEmail() != null) {
			applyEmail(user, dto.getEmail());
		}
		if (dto.getPhone() != null) {
			applyPhone(user, dto.getPhone());
		}
		if (dto.getRole() != null) {
			applyRole(user, dto.getRole());
		}

		try {
			userMapper.updateById(user);
		} catch (DuplicateKeyException ex) {
			throw BusinessException.conflict("邮箱已被使用");
		}
	}

	private void applyEmail(User user, String rawEmail) {
		String email = normalize(rawEmail);
		if (email == null) {
			return;
		}
		email = email.toLowerCase(Locale.ROOT);
		Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
			.eq(User::getEmail, email)
			.ne(User::getId, user.getId()));
		if (count != null && count > 0) {
			throw BusinessException.conflict("邮箱已被使用");
		}
		user.setEmail(email);
	}

	private void applyPhone(User user, String rawPhone) {
		String phone = normalize(rawPhone);
		if (phone == null) {
			return;
		}
		ObjectNode node = readProfileNode(user.getProfile());
		node.put("phone", phone);
		try {
			user.setProfile(objectMapper.writeValueAsString(node));
		} catch (IOException ex) {
			throw BusinessException.badRequest("用户联系方式保存失败");
		}
	}

	private void applyRole(User user, String rawRole) {
		String role = normalize(rawRole);
		if (role == null) {
			return;
		}
		if (!UserRoleEnum.MERCHANT_USER.getValue().equals(role)) {
			throw BusinessException.badRequest("内部接口只允许把用户升级为 merchant_user");
		}
		if (UserRoleEnum.MERCHANT_USER.getValue().equals(user.getRole())) {
			return;
		}
		if (!USER_ROLE_NORMAL(user.getRole())) {
			throw BusinessException.conflict("当前用户角色不支持升级为商家用户");
		}
		if (!STATUS_NORMAL.equals(user.getStatus())) {
			throw BusinessException.conflict("用户账号状态异常，无法升级为商家用户");
		}
		user.setRole(role);
	}

	private boolean USER_ROLE_NORMAL(String role) {
		return UserRoleEnum.NORMAL_USER.getValue().equals(role);
	}

	private ObjectNode readProfileNode(String rawProfile) {
		if (rawProfile == null || rawProfile.isBlank()) {
			return objectMapper.createObjectNode();
		}
		try {
			JsonNode node = objectMapper.readTree(rawProfile);
			if (node != null && node.isObject()) {
				return (ObjectNode) node;
			}
		} catch (IOException ignored) {
			// fall through to empty object
		}
		return objectMapper.createObjectNode();
	}

	private UserBriefVo toBrief(User user) {
		if (user == null) {
			return null;
		}
		UserBriefVo vo = new UserBriefVo();
		vo.setId(user.getId());
		vo.setUsername(user.getUsername());
		vo.setEmail(user.getEmail());
		vo.setRole(user.getRole());
		vo.setStatus(user.getStatus());
		vo.setProfile(user.getProfile());
		return vo;
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
