-- product-service + order-service shared schema (commerce_db)
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS commerce_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE commerce_db;

CREATE TABLE IF NOT EXISTS merchant_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商家主键ID',
    user_id INT UNSIGNED NOT NULL COMMENT '关联的用户表ID（逻辑外键）',
    merchant_name VARCHAR(100) NOT NULL COMMENT '商家名称',
    last_merchant_name_change_time DATETIME DEFAULT NULL COMMENT '上次修改商家名称的时间',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '商家图片URL',
    turnover DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '商家累计营业额（单位：元，精确到分）',
    description VARCHAR(2000) DEFAULT NULL COMMENT '商家简介（不超过500字）',
    status ENUM('pending', 'active', 'rejected', 'banned') NOT NULL COMMENT '商家状态：待审核/正常/已拒绝/已拉黑',
    audit_reason VARCHAR(255) COMMENT '审核拒绝理由',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_name (merchant_name),
    INDEX idx_user_id (user_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家信息表';

CREATE TABLE IF NOT EXISTS merchant_authentication_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商家认证主键ID',
    merchant_id INT UNSIGNED NOT NULL COMMENT '关联的商家信息表ID（逻辑外键）',
    authentication_name VARCHAR(100) NOT NULL COMMENT '认证名称',
    company_name VARCHAR(100) DEFAULT NULL COMMENT '公司名称',
    certificate_image_url VARCHAR(500) DEFAULT NULL COMMENT '证书图片URL',
    status ENUM('pending', 'active', 'rejected') NOT NULL DEFAULT 'active' COMMENT '认证状态',
    audit_reason VARCHAR(255) DEFAULT NULL COMMENT '审核说明',
    auth_level VARCHAR(100) DEFAULT NULL COMMENT '认证等级',
    valid_until DATE DEFAULT NULL COMMENT '认证有效期',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家认证表';

CREATE TABLE IF NOT EXISTS tag_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签主键ID',
    name VARCHAR(50) NOT NULL COMMENT '标签名称',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用标签表';

CREATE TABLE IF NOT EXISTS merchant_tag_rel_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商家标签关联主键ID',
    merchant_id INT UNSIGNED NOT NULL COMMENT '关联的商家信息表ID（逻辑外键）',
    tag_id INT UNSIGNED NOT NULL COMMENT '关联的通用标签表ID（逻辑外键）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_tag (merchant_id, tag_id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家标签关联表';

CREATE TABLE IF NOT EXISTS product_spu_tag_rel_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品标签关联主键ID',
    spu_id INT UNSIGNED NOT NULL COMMENT '关联的商品表ID（逻辑外键）',
    tag_id INT UNSIGNED NOT NULL COMMENT '关联的通用标签表ID（逻辑外键）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_tag (spu_id, tag_id),
    INDEX idx_product_spu_id (spu_id),
    INDEX idx_tag_id (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品标签关联表';

CREATE TABLE IF NOT EXISTS product_spu_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品SPU主键ID',
    merchant_id INT UNSIGNED NOT NULL COMMENT '关联的商家信息表ID（逻辑外键）',
    name VARCHAR(100) NOT NULL COMMENT '商品名称',
    description VARCHAR(2000) DEFAULT NULL COMMENT '商品简介（不超过500字）',
    image_url VARCHAR(500) NOT NULL COMMENT '商品SPU图片URL',
    status ENUM('draft', 'pending', 'rejected', 'published', 'taken_down') NOT NULL DEFAULT 'draft' COMMENT '商品状态',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'SPU是否有效（0表示无效，1表示有效）,用于软删除',
    audit_reason VARCHAR(255) DEFAULT NULL COMMENT '审核拒绝原因',
    min_price DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT '商品最低价格（单位：元，精确到分）',
    sales INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '商品销量',
    core_attributes JSON DEFAULT NULL COMMENT '商品核心属性（JSON数组，如["优质等级","重量"]）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_merchant_id (merchant_id),
    INDEX idx_name (name),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SPU表';

CREATE TABLE IF NOT EXISTS product_sku_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品SKU主键ID',
    spu_id INT UNSIGNED NOT NULL COMMENT '关联的商品SPU表ID（逻辑外键）',
    attribute_values JSON DEFAULT NULL COMMENT 'SKU属性值（JSON对象）',
    status ENUM('available', 'unavailable') NOT NULL DEFAULT 'available' COMMENT 'SKU状态',
    is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'SKU是否有效（0无效1有效），用于软删除',
    image_url VARCHAR(500) DEFAULT NULL COMMENT '商品SKU图片URL（可选）',
    price DECIMAL(15,2) NOT NULL COMMENT 'SKU单价（单位：元，精确到分）',
    stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'SKU库存量',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX idx_spu_id (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';

CREATE TABLE IF NOT EXISTS order_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单主键ID',
    order_no VARCHAR(64) NOT NULL COMMENT '订单号（业务唯一标识）',
    user_id INT UNSIGNED NOT NULL COMMENT '下单用户ID（逻辑外键）',
    spu_id INT UNSIGNED NOT NULL COMMENT '下单商品SPU ID（逻辑外键）',
    spu_name VARCHAR(100) NOT NULL COMMENT '下单商品名称（冗余存储）',
    sku_id INT UNSIGNED NOT NULL COMMENT '下单商品SKU ID（逻辑外键）',
    sku_attribute_values JSON DEFAULT NULL COMMENT '下单商品SKU属性值（冗余存储）',
    quantity INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '下单SKU数目',
    total_price DECIMAL(15,2) NOT NULL COMMENT '下单总价（单位：元，精确到分）',
    status ENUM('pending', 'shipped', 'completed', 'cancelled') NOT NULL DEFAULT 'pending' COMMENT '订单状态',
    buyer_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '买家是否从历史订单中删除（0否1是）',
    checkout_op_id VARCHAR(64) NULL COMMENT '下单编排 opId，用于超时补偿判断是否已落单',
    receiver_name VARCHAR(100) NULL COMMENT '收件人姓名',
    receiver_phone VARCHAR(30) NULL COMMENT '收件人手机号',
    receiver_address VARCHAR(500) NULL COMMENT '收件地址',
    payment_method VARCHAR(30) NULL COMMENT '支付方式',
    tracking_no VARCHAR(64) NULL COMMENT '物流单号',
    shipping_progress VARCHAR(100) NULL COMMENT '物流进度描述',
    current_node VARCHAR(100) NULL COMMENT '当前物流节点',
    map_x DECIMAL(5,2) NULL COMMENT '物流地图X坐标',
    map_y DECIMAL(5,2) NULL COMMENT '物流地图Y坐标',
    cancel_reason VARCHAR(255) NULL COMMENT '取消原因',
    completed_time DATETIME NULL COMMENT '完成时间',
    review_rating INT NULL COMMENT '买家评分',
    review_content VARCHAR(500) NULL COMMENT '买家评价内容',
    review_time DATETIME NULL COMMENT '评价时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间（精确到时分秒）',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    INDEX idx_user_id (user_id),
    INDEX idx_spu_id (spu_id),
    INDEX idx_status (status),
    INDEX idx_checkout_op_id (checkout_op_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- product-service: inventory op idempotency/state
CREATE TABLE IF NOT EXISTS inventory_op_tb (
    op_id VARCHAR(64) NOT NULL,
    op_type VARCHAR(20) NOT NULL COMMENT 'reserve/release',
    status VARCHAR(20) NOT NULL COMMENT 'RESERVED/RELEASED/FAILED',
    result_json TEXT NULL COMMENT 'reserve 返回快照（含 skuId/quantity/lineId）',
    user_id BIGINT NULL COMMENT '下单用户，便于审计',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (op_id, op_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存操作幂等状态表';

-- product-service: merchant approval role-sync outbox
CREATE TABLE IF NOT EXISTS merchant_role_sync_tb (
    merchant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    target_role VARCHAR(30) NOT NULL COMMENT 'merchant_user',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/DONE/FAILED',
    last_error VARCHAR(500) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家审核角色同步 outbox';

-- order-service: checkout saga outbox/state
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='下单编排 outbox/状态机';

-- order-service: cancel/return inventory release outbox
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='取消/退货库存回补 outbox';

-- Seed merchant referencing user_db.user_tb.id = 1
INSERT INTO merchant_tb (user_id, merchant_name, image_url, turnover, description, status)
VALUES (1, '爱宠乐园', NULL, 0.00, '测试商家', 'active')
ON DUPLICATE KEY UPDATE merchant_name = VALUES(merchant_name);
