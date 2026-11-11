package com.edu.csu.web.pet_shop_backend.service.impl;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.entity.dto.UserProfileUpdateDto;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import com.edu.csu.web.pet_shop_backend.entity.vo.UserProfileVo;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.mapper.UserMapper;
import com.edu.csu.web.pet_shop_backend.service.UserProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class UserProfileServiceImpl implements UserProfileService {

	private static final String DEFAULT_AVATAR = "/images/logo192.png";
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final UserMapper userMapper;
	private final ObjectMapper objectMapper;

	public UserProfileServiceImpl(UserMapper userMapper, ObjectMapper objectMapper) {
		this.userMapper = userMapper;
		this.objectMapper = objectMapper;
	}

	@Override
	public UserProfileVo getProfileForCurrentUser(Long userId) {
		User user = requireUser(userId);
		return buildVo(user);
	}

	@Override
	public UserProfileVo updateProfileForCurrentUser(UserProfileUpdateDto request, Long userId) {
		User user = requireUser(userId);
		validateUniqueFields(user, request);

		if (request.getUsername() != null) {
			user.setUsername(normalizeNullable(request.getUsername()));
		}
		if (request.getEmail() != null) {
			user.setEmail(normalizeNullable(request.getEmail()));
		}

		BuyerProfileExtra extra = readExtra(user.getProfile());
		if (request.getPhone() != null) {
			extra.setPhone(normalizeNullable(request.getPhone()));
		}
		if (request.getIntro() != null) {
			extra.setIntro(normalizeNullable(request.getIntro()));
		}
		if (request.getAddress() != null) {
			extra.setAddress(normalizeNullable(request.getAddress()));
		}
		if (request.getPreferences() != null) {
			extra.setPreferences(normalizePreferences(request.getPreferences()));
		}
		user.setProfile(writeExtra(extra));

		if (userMapper.updateById(user) <= 0) {
			throw BusinessException.notFound("用户不存在");
		}

		return buildVo(requireUser(userId));
	}

	private User requireUser(Long userId) {
		if (userId == null) {
			throw BusinessException.unauthorized("未登录或登录已失效");
		}
		User user = userMapper.selectById(userId);
		if (user == null) {
			throw BusinessException.notFound("用户不存在");
		}
		if ("deleted".equals(user.getStatus())) {
			throw BusinessException.unauthorized("账号已注销");
		}
		return user;
	}

	private void validateUniqueFields(User currentUser, UserProfileUpdateDto request) {
		String nextUsername = normalizeNullable(request.getUsername());
		if (nextUsername != null && !nextUsername.equals(currentUser.getUsername())) {
			User existed = userMapper.selectOne(new LambdaQueryWrapper<User>()
				.eq(User::getUsername, nextUsername)
				.ne(User::getId, currentUser.getId())
				.last("LIMIT 1"));
			if (existed != null) {
				throw BusinessException.conflict("用户名已存在");
			}
		}

		String nextEmail = normalizeNullable(request.getEmail());
		if (nextEmail != null && !nextEmail.equals(currentUser.getEmail())) {
			User existed = userMapper.selectOne(new LambdaQueryWrapper<User>()
				.eq(User::getEmail, nextEmail)
				.ne(User::getId, currentUser.getId())
				.last("LIMIT 1"));
			if (existed != null) {
				throw BusinessException.conflict("邮箱已被使用");
			}
		}
	}

	private UserProfileVo buildVo(User user) {
		BuyerProfileExtra extra = readExtra(user.getProfile());
		UserProfileVo vo = new UserProfileVo();
		vo.setId(String.valueOf(user.getId()));
		vo.setUsername(defaultString(user.getUsername()));
		vo.setEmail(defaultString(user.getEmail()));
		vo.setPhone(defaultString(extra.getPhone()));
		vo.setIntro(defaultString(extra.getIntro()));
		vo.setAvatar(defaultString(extra.getAvatar(), DEFAULT_AVATAR));
		vo.setJoinedAt(formatDate(user.getCreateTime()));
		vo.setAddress(defaultString(extra.getAddress()));
		vo.setPreferences(extra.getPreferences() == null ? List.of() : extra.getPreferences());
		return vo;
	}

	private BuyerProfileExtra readExtra(String rawProfile) {
		if (rawProfile == null || rawProfile.isBlank()) {
			return defaultExtra();
		}
		try {
			BuyerProfileExtra extra = objectMapper.readValue(rawProfile, BuyerProfileExtra.class);
			if (extra == null) {
				return defaultExtra();
			}
			if (extra.getPreferences() == null) {
				extra.setPreferences(new ArrayList<>());
			}
			if (normalizeNullable(extra.getAvatar()) == null) {
				extra.setAvatar(DEFAULT_AVATAR);
			}
			return extra;
		} catch (IOException ex) {
			return defaultExtra();
		}
	}

	private String writeExtra(BuyerProfileExtra extra) {
		try {
			return objectMapper.writeValueAsString(extra);
		} catch (IOException ex) {
			throw BusinessException.badRequest("用户资料序列化失败");
		}
	}

	private BuyerProfileExtra defaultExtra() {
		BuyerProfileExtra extra = new BuyerProfileExtra();
		extra.setAvatar(DEFAULT_AVATAR);
		extra.setPreferences(new ArrayList<>());
		return extra;
	}

	private List<String> normalizePreferences(List<String> preferences) {
		List<String> normalized = new ArrayList<>();
		for (String preference : preferences) {
			String value = normalizeNullable(preference);
			if (value != null) {
				normalized.add(value);
			}
		}
		return normalized;
	}

	private String normalizeNullable(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private String defaultString(String value) {
		return defaultString(value, "");
	}

	private String defaultString(String value, String fallback) {
		return value == null ? fallback : value;
	}

	private String formatDate(LocalDateTime time) {
		if (time == null) {
			return "";
		}
		return DATE_FMT.format(time.toLocalDate());
	}

	private static class BuyerProfileExtra {
		private String phone;
		private String intro;
		private String avatar;
		private String address;
		private List<String> preferences;

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}

		public String getIntro() {
			return intro;
		}

		public void setIntro(String intro) {
			this.intro = intro;
		}

		public String getAvatar() {
			return avatar;
		}

		public void setAvatar(String avatar) {
			this.avatar = avatar;
		}

		public String getAddress() {
			return address;
		}

		public void setAddress(String address) {
			this.address = address;
		}

		public List<String> getPreferences() {
			return preferences;
		}

		public void setPreferences(List<String> preferences) {
			this.preferences = preferences;
		}
	}
}