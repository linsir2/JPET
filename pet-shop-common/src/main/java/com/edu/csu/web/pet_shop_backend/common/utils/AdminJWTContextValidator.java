package com.edu.csu.web.pet_shop_backend.common.utils;

import com.edu.csu.web.pet_shop_backend.entity.pojo.JWTUserContext;
import com.edu.csu.web.pet_shop_backend.enums.UserRoleEnum;
import com.edu.csu.web.pet_shop_backend.exception.BusinessException;

public final class AdminJWTContextValidator {

	private AdminJWTContextValidator() {
	}

	public static void requireAdmin(JWTUserContext jwtUserContext) {
		if (jwtUserContext == null) {
			throw BusinessException.unauthorized("未登录或登录已失效");
		}

		UserRoleEnum role = jwtUserContext.getRole();
		if (role != UserRoleEnum.MAIN_ADMIN && role != UserRoleEnum.SUB_ADMIN) {
			throw BusinessException.forbidden("当前账号无权访问该资源");
		}
	}
}