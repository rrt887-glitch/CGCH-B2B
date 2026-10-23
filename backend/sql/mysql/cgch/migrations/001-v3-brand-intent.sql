-- v3 改版：咨询意图字段、品牌名称、停用演示账号（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
ALTER TABLE `channel_wa_conversation` ADD COLUMN `intent` varchar(255) NULL COMMENT '咨询意图：sales 销售咨询、after_sales 售后支持、business 商务合作、invalid 无效咨询；为空表示未分类';
ALTER TABLE `channel_mail_message` ADD COLUMN `intent` varchar(255) NULL COMMENT '咨询意图（客户来信）：sales、after_sales、business、invalid；为空表示未分类';
UPDATE `system_tenant` SET `name` = '草根出海', `contact_name` = '草根出海', `websites` = 'hig.global,www.hig.global' WHERE `id` = 1;
UPDATE `system_users` SET `status` = 1 WHERE `id` <> 1 AND `id` <= 240;
UPDATE `system_tenant` SET `status` = 1 WHERE `id` <> 1;
