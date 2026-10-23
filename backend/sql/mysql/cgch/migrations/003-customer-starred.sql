-- 客户「特别关注」（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
ALTER TABLE `crm_customer` ADD COLUMN `starred` bit(1) NULL DEFAULT b'0' COMMENT '是否特别关注';
