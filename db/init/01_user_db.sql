-- user-service schema (user_db)
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS user_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE user_db;

CREATE TABLE IF NOT EXISTS user_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) NOT NULL COMMENT '邮箱',
    password VARCHAR(255) NOT NULL COMMENT '密码（存储加密后的哈希值）',
    role ENUM('normal_user', 'merchant_user') NOT NULL COMMENT '用户角色',
    status ENUM('normal', 'banned', 'deleted') NOT NULL COMMENT '用户状态：正常/拉黑/已注销',
    profile VARCHAR(500) DEFAULT NULL COMMENT '个人简介',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    INDEX idx_role (role),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE IF NOT EXISTS administrator_tb (
    id INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    account VARCHAR(50) NOT NULL COMMENT '管理员账号',
    password VARCHAR(255) NOT NULL COMMENT '密码（存储加密后的哈希值）',
    role ENUM('main_admin', 'sub_admin') NOT NULL COMMENT '管理员角色：主管理员/子管理员',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_account (account)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- Seed: fixed id=1 so commerce_db seed can reference it.
INSERT INTO user_tb (id, username, email, password, role, status, profile)
VALUES (1, 'test_user_1', 'test_user_1@example.com', 'password_hash', 'merchant_user', 'normal', '测试用户')
ON DUPLICATE KEY UPDATE username = VALUES(username);
