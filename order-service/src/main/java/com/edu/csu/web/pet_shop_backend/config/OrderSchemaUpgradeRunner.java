package com.edu.csu.web.pet_shop_backend.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * commerce_db 中 order-service 负责的终态升级。
 * 新库由 db/init/02_commerce_db.sql 直接建终态；这里兼容旧库。
 */
@Component
public class OrderSchemaUpgradeRunner implements ApplicationRunner {

	private final JdbcTemplate jdbcTemplate;

	public OrderSchemaUpgradeRunner(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensureOrderColumns();
		createCheckoutOpTable();
		createOrderInventoryOpTable();
		ensureIndex("order_tb", "idx_checkout_op_id",
			"CREATE INDEX idx_checkout_op_id ON order_tb (checkout_op_id)");
	}

	private void ensureOrderColumns() {
		addColumnIfMissing("order_tb", "receiver_name",
			"ALTER TABLE order_tb ADD COLUMN receiver_name VARCHAR(100) NULL COMMENT '收件人姓名' AFTER status");
		addColumnIfMissing("order_tb", "receiver_phone",
			"ALTER TABLE order_tb ADD COLUMN receiver_phone VARCHAR(30) NULL COMMENT '收件人手机号' AFTER receiver_name");
		addColumnIfMissing("order_tb", "receiver_address",
			"ALTER TABLE order_tb ADD COLUMN receiver_address VARCHAR(500) NULL COMMENT '收件地址' AFTER receiver_phone");
		addColumnIfMissing("order_tb", "payment_method",
			"ALTER TABLE order_tb ADD COLUMN payment_method VARCHAR(30) NULL COMMENT '支付方式' AFTER receiver_address");
		addColumnIfMissing("order_tb", "tracking_no",
			"ALTER TABLE order_tb ADD COLUMN tracking_no VARCHAR(64) NULL COMMENT '物流单号' AFTER payment_method");
		addColumnIfMissing("order_tb", "shipping_progress",
			"ALTER TABLE order_tb ADD COLUMN shipping_progress VARCHAR(100) NULL COMMENT '物流进度描述' AFTER tracking_no");
		addColumnIfMissing("order_tb", "current_node",
			"ALTER TABLE order_tb ADD COLUMN current_node VARCHAR(100) NULL COMMENT '当前物流节点' AFTER shipping_progress");
		addColumnIfMissing("order_tb", "map_x",
			"ALTER TABLE order_tb ADD COLUMN map_x DECIMAL(5,2) NULL COMMENT '物流地图X坐标' AFTER current_node");
		addColumnIfMissing("order_tb", "map_y",
			"ALTER TABLE order_tb ADD COLUMN map_y DECIMAL(5,2) NULL COMMENT '物流地图Y坐标' AFTER map_x");
		addColumnIfMissing("order_tb", "cancel_reason",
			"ALTER TABLE order_tb ADD COLUMN cancel_reason VARCHAR(255) NULL COMMENT '取消原因' AFTER map_y");
		addColumnIfMissing("order_tb", "completed_time",
			"ALTER TABLE order_tb ADD COLUMN completed_time DATETIME NULL COMMENT '完成时间' AFTER cancel_reason");
		addColumnIfMissing("order_tb", "review_rating",
			"ALTER TABLE order_tb ADD COLUMN review_rating INT NULL COMMENT '买家评分' AFTER completed_time");
		addColumnIfMissing("order_tb", "review_content",
			"ALTER TABLE order_tb ADD COLUMN review_content VARCHAR(500) NULL COMMENT '买家评价内容' AFTER review_rating");
		addColumnIfMissing("order_tb", "review_time",
			"ALTER TABLE order_tb ADD COLUMN review_time DATETIME NULL COMMENT '评价时间' AFTER review_content");
		addColumnIfMissing("order_tb", "buyer_deleted",
			"ALTER TABLE order_tb ADD COLUMN buyer_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '买家是否从历史订单中删除' AFTER status");
		addColumnIfMissing("order_tb", "checkout_op_id",
			"ALTER TABLE order_tb ADD COLUMN checkout_op_id VARCHAR(64) NULL COMMENT '下单编排 opId' AFTER buyer_deleted");
	}

	private void createCheckoutOpTable() {
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS checkout_op_tb (
				op_id VARCHAR(64) NOT NULL,
				user_id BIGINT NOT NULL,
				status VARCHAR(20) NOT NULL COMMENT 'RESERVING/RESERVED/COMMITTED/COMPENSATING/COMPENSATED/FAILED',
				items_json TEXT NOT NULL COMMENT '请求 items 快照',
				reserve_result_json TEXT NULL COMMENT 'reserve 返回快照',
				last_error VARCHAR(500) NULL,
				create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
				update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
				PRIMARY KEY (op_id),
				INDEX idx_status (status),
				INDEX idx_user_id (user_id)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下单编排 outbox/状态机'
			""");
	}

	private void createOrderInventoryOpTable() {
		jdbcTemplate.execute("""
			CREATE TABLE IF NOT EXISTS order_inventory_op_tb (
				op_id VARCHAR(64) NOT NULL COMMENT 'release:{orderId}:{cancel|return}',
				order_id BIGINT NOT NULL,
				action VARCHAR(20) NOT NULL COMMENT 'cancel/return',
				items_json TEXT NOT NULL COMMENT 'skuId/spuId/quantity 快照',
				status VARCHAR(20) NOT NULL COMMENT 'PENDING/DONE/FAILED',
				last_error VARCHAR(500) NULL,
				create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
				update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
				PRIMARY KEY (op_id),
				INDEX idx_status (status),
				INDEX idx_order_id (order_id)
			) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='取消/退货库存回补 outbox'
			""");
	}

	private void addColumnIfMissing(String tableName, String columnName, String ddl) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
			Integer.class,
			tableName,
			columnName);
		if (count != null && count == 0) {
			jdbcTemplate.execute(ddl);
		}
	}

	private void ensureIndex(String tableName, String indexName, String ddl) {
		Integer count = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
			Integer.class,
			tableName,
			indexName);
		if (count != null && count == 0) {
			jdbcTemplate.execute(ddl);
		}
	}
}
