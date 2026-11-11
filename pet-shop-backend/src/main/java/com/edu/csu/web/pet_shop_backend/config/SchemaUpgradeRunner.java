package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SchemaUpgradeRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public SchemaUpgradeRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureMerchantAuthenticationColumns();
        createInventoryOpTable();
    }

    private void ensureMerchantAuthenticationColumns() {
        addColumnIfMissing("merchant_authentication_tb", "company_name",
            "ALTER TABLE merchant_authentication_tb ADD COLUMN company_name VARCHAR(100) NULL COMMENT '公司名称' AFTER authentication_name");
        addColumnIfMissing("merchant_authentication_tb", "certificate_image_url",
            "ALTER TABLE merchant_authentication_tb ADD COLUMN certificate_image_url VARCHAR(500) NULL COMMENT '证书图片URL' AFTER company_name");
        addColumnIfMissing("merchant_authentication_tb", "status",
            "ALTER TABLE merchant_authentication_tb ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '认证状态' AFTER certificate_image_url");
        addColumnIfMissing("merchant_authentication_tb", "audit_reason",
            "ALTER TABLE merchant_authentication_tb ADD COLUMN audit_reason VARCHAR(255) NULL COMMENT '审核说明' AFTER status");
        addColumnIfMissing("merchant_authentication_tb", "auth_level",
            "ALTER TABLE merchant_authentication_tb ADD COLUMN auth_level VARCHAR(100) NULL COMMENT '认证等级' AFTER audit_reason");
        addColumnIfMissing("merchant_authentication_tb", "valid_until",
            "ALTER TABLE merchant_authentication_tb ADD COLUMN valid_until DATE NULL COMMENT '认证有效期' AFTER auth_level");

        jdbcTemplate.update(
            "UPDATE merchant_authentication_tb SET status = 'active' WHERE status IS NULL OR status = ''");
        jdbcTemplate.update(
            "UPDATE merchant_authentication_tb SET company_name = authentication_name WHERE company_name IS NULL OR company_name = ''");
        jdbcTemplate.update(
            "UPDATE merchant_authentication_tb SET auth_level = '历史认证' WHERE auth_level IS NULL OR auth_level = ''");
    }

    private void createInventoryOpTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS inventory_op_tb (
                op_id VARCHAR(64) NOT NULL,
                op_type VARCHAR(20) NOT NULL,
                status VARCHAR(20) NOT NULL,
                result_json TEXT NULL,
                user_id BIGINT NULL,
                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (op_id, op_type)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存操作幂等状态表'
            """);
    }

    private void addColumnIfMissing(String tableName, String columnName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
            Integer.class,
            tableName,
            columnName
        );
        if (count != null && count == 0) {
            jdbcTemplate.execute(ddl);
        }
    }
}