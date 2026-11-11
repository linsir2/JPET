package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * user_db 终态升级：把 status 枚举扩到 normal/banned/deleted。
 * 新库由 db/init/01_user_db.sql 直接建终态；这里兼容旧库。
 */
@Component
public class UserSchemaUpgradeRunner implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	public UserSchemaUpgradeRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'user_tb'",
			Integer.class);
		if (count == null || count == 0) {
			return;
		}
		jdbcTemplate.execute(
			"ALTER TABLE user_tb MODIFY status ENUM('normal','banned','deleted') NOT NULL");
	}
}
