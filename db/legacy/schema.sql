-- 用户表
CREATE TABLE user_tb (
                         id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                         username VARCHAR(50) NOT NULL COMMENT '用户名',
                         email VARCHAR(100) NOT NULL COMMENT '邮箱',
                         password VARCHAR(255) NOT NULL COMMENT '密码（存储加密后的哈希值）',
                         role ENUM('normal_user', 'merchant_user') NOT NULL COMMENT '用户角色',
                         status ENUM('normal', 'banned') NOT NULL COMMENT '用户状态：正常/拉黑',
                         profile VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
                         create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (id),
                         UNIQUE KEY uk_username (username),
                         UNIQUE KEY uk_email (email),
                         INDEX idx_role (role),
                         INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';


-- 管理员表
CREATE TABLE administrator_tb (
                                  id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
                                  account VARCHAR(50) NOT NULL COMMENT '管理员账号',
                                  password VARCHAR(255) NOT NULL COMMENT '密码（建议存储加密后的哈希值）',
                                  role ENUM('main_admin', 'sub_admin') NOT NULL COMMENT '管理员角色：主管理员/子管理员',
                                  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  PRIMARY KEY (id),
                                  UNIQUE KEY uk_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';


-- 商家信息表
CREATE TABLE merchant_tb (
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


-- 商家认证表
CREATE TABLE merchant_authentication_tb (
                                            id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商家认证主键ID',
                                            merchant_id INT UNSIGNED NOT NULL COMMENT '关联的商家信息表ID（逻辑外键）',
                                            authentication_name VARCHAR(100) NOT NULL COMMENT '认证名称（如营业执照认证、食品经营许可证认证）',
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


-- 通用标签表
CREATE TABLE tag_tb (
                        id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '标签主键ID',
                        name VARCHAR(50) NOT NULL COMMENT '标签名称',
                        create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (id),
                        UNIQUE KEY uk_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用标签表';


-- 商家标签关联表
CREATE TABLE merchant_tag_rel_tb (
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


-- 商品标签关联表
CREATE TABLE product_spu_tag_rel_tb (
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


-- 商品 SPU 表
CREATE TABLE product_spu_tb (
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


-- 商品 SKU 表
CREATE TABLE product_sku_tb (
                                id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '商品SKU主键ID',
                                spu_id INT UNSIGNED NOT NULL COMMENT '关联的商品SPU表ID（逻辑外键）',
                                attribute_values JSON DEFAULT NULL COMMENT 'SKU属性值（JSON对象，如{"优质等级":"2","重量":"2000g"}）',
                                status ENUM('available', 'unavailable') NOT NULL DEFAULT 'available' COMMENT 'SKU状态',
                                is_active TINYINT(1) NOT NULL DEFAULT 1 COMMENT 'SKU是否有效（0表示无效，1表示有效）,用于软删除',
                                image_url VARCHAR(500) DEFAULT NULL COMMENT '商品SKU图片URL（可选）',
                                price DECIMAL(15,2) NOT NULL COMMENT 'SKU单价（单位：元，精确到分）',
                                stock INT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'SKU库存量',
                                create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (id),
                                INDEX idx_spu_id (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品SKU表';


-- 订单表
CREATE TABLE order_tb (
                          id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '订单主键ID',
                          order_no VARCHAR(64) NOT NULL COMMENT '订单号（业务唯一标识）',
                          user_id INT UNSIGNED NOT NULL COMMENT '下单用户ID（逻辑外键）',
                          spu_id INT UNSIGNED NOT NULL COMMENT '下单商品SPU ID（逻辑外键）',
                          spu_name VARCHAR(100) NOT NULL COMMENT '下单商品名称（冗余存储，避免SPU改名影响历史订单）',
                          sku_id INT UNSIGNED NOT NULL COMMENT '下单商品SKU ID（逻辑外键）',
                          sku_attribute_values JSON DEFAULT NULL COMMENT '下单商品SKU属性值（冗余存储，避免SKU属性修改影响历史订单）',
                          quantity INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '下单SKU数目',
                          total_price DECIMAL(15,2) NOT NULL COMMENT '下单总价（单位：元，精确到分）',
                          status ENUM('pending', 'shipped', 'completed', 'cancelled') NOT NULL DEFAULT 'pending' COMMENT '订单状态',
                          create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间（精确到时分秒）',
                          update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          PRIMARY KEY (id),
                          UNIQUE KEY uk_order_no (order_no),
                          INDEX idx_user_id (user_id),
                          INDEX idx_spu_id (spu_id),
                          INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';