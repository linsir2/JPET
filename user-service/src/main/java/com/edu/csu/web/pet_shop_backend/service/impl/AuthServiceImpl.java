package com.edu.csu.web.pet_shop_backend.service.impl;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.edu.csu.web.pet_shop_backend.config.AdminRegistrationProperties;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.common.utils.JwtUtil;
import com.edu.csu.web.pet_shop_backend.common.utils.PasswordUtil;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminLoginDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.AdminRegisterDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.MerchantLoginDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.TokenRefreshDto;
import com.edu.csu.web.pet_shop_backend.entity.dto.UserRegisterDto;
import com.edu.csu.web.pet_shop_backend.entity.model.User;
import com.edu.csu.web.pet_shop_backend.entity.model.Administrator;
import com.edu.csu.web.pet_shop_backend.entity.vo.LoginVo;
import com.edu.csu.web.pet_shop_backend.entity.vo.TokenRefreshVo;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.exception.AccountStatusInvalidException;
import com.edu.csu.web.pet_shop_backend.exception.AdminRegisterConflictException;
import com.edu.csu.web.pet_shop_backend.exception.AdminRegisterKeyInvalidException;
import com.edu.csu.web.pet_shop_backend.exception.AuthCredentialsInvalidException;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;
import com.edu.csu.web.pet_shop_backend.exception.UserRegisterConflictException;
import com.edu.csu.web.pet_shop_backend.mapper.AdministratorMapper;
import com.edu.csu.web.pet_shop_backend.mapper.UserMapper;
import com.edu.csu.web.pet_shop_backend.service.AuthService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

/**
 * 认证服务实现，负责用户注册、商家/管理员登录与 token 刷新。
 */
@Service
public class AuthServiceImpl implements AuthService {

	private static final String USER_STATUS_NORMAL = "normal";
	private static final String USER_STATUS_BANNED = "banned";

	private final UserMapper userMapper;
	private final AdministratorMapper administratorMapper;
	private final PasswordUtil passwordUtil;
	private final JwtUtil jwtUtil;
	private final AdminRegistrationProperties adminRegistrationProperties;

	public AuthServiceImpl(UserMapper userMapper, AdministratorMapper administratorMapper, PasswordUtil passwordUtil,
			JwtUtil jwtUtil, AdminRegistrationProperties adminRegistrationProperties) {
		this.userMapper = userMapper;
		this.administratorMapper = administratorMapper;
		this.passwordUtil = passwordUtil;
		this.jwtUtil = jwtUtil;
		this.adminRegistrationProperties = adminRegistrationProperties;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void registerUser(UserRegisterDto registerDto) {
		String username = normalizeRequired(registerDto.getUsername());
		String email = normalizeEmail(registerDto.getEmail());
		String password = registerDto.getPassword();

		if (existsByUsername(username)) {
			throw new UserRegisterConflictException("username", username);
		}
		if (existsByEmail(email)) {
			throw new UserRegisterConflictException("email", email);
		}

		User user = new User();
		user.setUsername(username);
		user.setEmail(email);
		user.setPassword(passwordUtil.encode(password));
		user.setRole(UserRoleEnum.NORMAL_USER.getValue());
		user.setStatus(USER_STATUS_NORMAL);
		userMapper.insert(user);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void registerAdmin(AdminRegisterDto registerDto) {
		String account = normalizeRequired(registerDto.getAccount());
		String registerKey = normalizeRequired(registerDto.getRegisterKey());

		if (existsAdminByAccount(account)) {
			throw new AdminRegisterConflictException(account);
		}

		UserRoleEnum role = resolveAdminRole(registerKey, account);

		Administrator administrator = new Administrator();
		administrator.setAccount(account);
		administrator.setPassword(passwordUtil.encode(registerDto.getPassword()));
		administrator.setRole(role.getValue());
		administratorMapper.insert(administrator);
	}

	@Override
	public LoginVo loginMerchant(MerchantLoginDto loginDto) {
		String identifier = normalizeRequired(loginDto.getIdentifier());

		User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
			.nested(wrapper -> wrapper.eq(User::getUsername, identifier).or().eq(User::getEmail, identifier)));
		if (user == null || !passwordUtil.matches(loginDto.getPassword(), user.getPassword())) {
			throw new AuthCredentialsInvalidException("merchant_login", identifier, "用户名/邮箱或密码错误");
		}

		ensureUserStatusValid(user);
		UserRoleEnum role = UserRoleEnum.fromValue(user.getRole());
		return buildLoginVo(user.getId(), user.getUsername(), role);
	}

	@Override
	public LoginVo loginAdmin(AdminLoginDto loginDto) {
		String account = normalizeRequired(loginDto.getAccount());

		Administrator administrator = administratorMapper.selectOne(
			new LambdaQueryWrapper<Administrator>().eq(Administrator::getAccount, account));
		if (administrator == null || !passwordUtil.matches(loginDto.getPassword(), administrator.getPassword())) {
			throw new AuthCredentialsInvalidException("admin_login", account, "管理员账号或密码错误");
		}

		UserRoleEnum role = UserRoleEnum.fromValue(administrator.getRole());
		return buildLoginVo(administrator.getId(), administrator.getAccount(), role);
	}

	@Override
	public TokenRefreshVo refreshAccessToken(TokenRefreshDto refreshDto) {
		String refreshToken = normalizeRequired(refreshDto.getRefreshToken());

		try {
			Claims claims = jwtUtil.parseToken(refreshToken);
			if (!jwtUtil.isRefreshToken(claims)) {
				throw new AuthCredentialsInvalidException("refresh_token", null, "当前凭证不是 refresh_token");
			}

			Long subjectId = jwtUtil.getUserId(claims);
			UserRoleEnum tokenRole = jwtUtil.getRole(claims);
			String accessToken;
			if (tokenRole == UserRoleEnum.MAIN_ADMIN || tokenRole == UserRoleEnum.SUB_ADMIN) {
				Administrator admin = administratorMapper.selectById(subjectId);
				if (admin == null) {
					throw new AuthCredentialsInvalidException("refresh_token", null, "账号不存在");
				}
				accessToken = jwtUtil.generateAccessToken(
					admin.getId(),
					admin.getAccount(),
					UserRoleEnum.fromValue(admin.getRole())
				);
			} else {
				User user = userMapper.selectById(subjectId);
				if (user == null) {
					throw new AuthCredentialsInvalidException("refresh_token", null, "账号不存在");
				}
				ensureUserStatusValid(user);
				accessToken = jwtUtil.generateAccessToken(
					user.getId(),
					user.getUsername(),
					UserRoleEnum.fromValue(user.getRole())
				);
			}
			return new TokenRefreshVo(accessToken);
		} catch (JwtException | IllegalArgumentException ex) {
			throw new AuthCredentialsInvalidException("refresh_token", null, "refresh_token 无效或已过期");
		}
	}

	private LoginVo buildLoginVo(Long subjectId, String principal, UserRoleEnum role) {
		String accessToken = jwtUtil.generateAccessToken(subjectId, principal, role);
		String refreshToken = jwtUtil.generateRefreshToken(subjectId, principal, role);
		return new LoginVo(accessToken, refreshToken, role.getValue());
	}

	private void ensureUserStatusValid(User user) {
		if ("deleted".equals(user.getStatus())) {
			throw new AccountStatusInvalidException("user", user.getId(), user.getStatus(), "账号已注销");
		}
		if (USER_STATUS_BANNED.equals(user.getStatus())) {
			throw new AccountStatusInvalidException("user", user.getId(), user.getStatus(), "当前账号已被拉黑，无法登录");
		}
		if (!USER_STATUS_NORMAL.equals(user.getStatus())) {
			throw new AccountStatusInvalidException("user", user.getId(), user.getStatus(), "当前账号状态异常，无法登录");
		}
	}

	private boolean existsByUsername(String username) {
		return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
	}

	private boolean existsByEmail(String email) {
		return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
	}

	private boolean existsAdminByAccount(String account) {
		return administratorMapper.selectCount(new LambdaQueryWrapper<Administrator>()
			.eq(Administrator::getAccount, account)) > 0;
	}

	private UserRoleEnum resolveAdminRole(String registerKey, String account) {
		String mainAdminKey = normalizeRequired(adminRegistrationProperties.getMainAdminKey());
		String subAdminKey = normalizeRequired(adminRegistrationProperties.getSubAdminKey());

		if (mainAdminKey != null && mainAdminKey.equals(registerKey)) {
			return UserRoleEnum.MAIN_ADMIN;
		}
		if (subAdminKey != null && subAdminKey.equals(registerKey)) {
			return UserRoleEnum.SUB_ADMIN;
		}

		throw new AdminRegisterKeyInvalidException(account);
	}

	private String normalizeRequired(String value) {
		return value == null ? null : value.trim();
	}

	private String normalizeEmail(String email) {
		String trimmedEmail = normalizeRequired(email);
		return trimmedEmail == null ? null : trimmedEmail.toLowerCase(Locale.ROOT);
	}
}