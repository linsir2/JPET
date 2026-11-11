package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.csu.web.pet_shop_backend.common.utils.PasswordUtil;
import com.edu.csu.web.pet_shop_backend.entity.model.Administrator;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.mapper.AdministratorMapper;

@Component
public class DefaultAdminInitializer implements ApplicationRunner {

	private final AdministratorMapper administratorMapper;
	private final PasswordUtil passwordUtil;
	private final DefaultAdminProperties defaultAdminProperties;

	public DefaultAdminInitializer(
		AdministratorMapper administratorMapper,
		PasswordUtil passwordUtil,
		DefaultAdminProperties defaultAdminProperties
	) {
		this.administratorMapper = administratorMapper;
		this.passwordUtil = passwordUtil;
		this.defaultAdminProperties = defaultAdminProperties;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!defaultAdminProperties.isEnabled()) {
			return;
		}

		String account = normalize(defaultAdminProperties.getAccount());
		String password = normalize(defaultAdminProperties.getPassword());
		String role = normalize(defaultAdminProperties.getRole());

		if (account == null || password == null) {
			return;
		}

		String normalizedRole = role == null ? UserRoleEnum.MAIN_ADMIN.getValue() : role;
		Administrator admin = administratorMapper.selectOne(
			new LambdaQueryWrapper<Administrator>().eq(Administrator::getAccount, account)
		);

		if (admin == null) {
			Administrator created = new Administrator();
			created.setAccount(account);
			created.setPassword(passwordUtil.encode(password));
			created.setRole(normalizedRole);
			administratorMapper.insert(created);
			return;
		}

		boolean changed = false;
		if (!passwordUtil.matches(password, admin.getPassword())) {
			admin.setPassword(passwordUtil.encode(password));
			changed = true;
		}
		if (!normalizedRole.equals(admin.getRole())) {
			admin.setRole(normalizedRole);
			changed = true;
		}

		if (changed) {
			administratorMapper.updateById(admin);
		}
	}

	private String normalize(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}