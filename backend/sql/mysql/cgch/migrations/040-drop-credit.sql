-- 040：移除智点（计费）模块
--
-- 本产品的 AI 由客户自己配置模型 API Key，费用结算在客户的模型账户，平台不代付，
-- 所以余额 / 充值 / 扣点全部下线。可重复执行。

DROP TABLE IF EXISTS `channel_credit_ledger`;
DROP TABLE IF EXISTS `channel_credit_order`;
DROP TABLE IF EXISTS `channel_credit_account`;

-- 记录级的 credits 计数列一并删除（AI 不再扣点）

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_case' AND COLUMN_NAME = 'credits');
SET @ddl := IF(@has = 1, 'ALTER TABLE `channel_ai_case` DROP COLUMN `credits`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_draft' AND COLUMN_NAME = 'credits');
SET @ddl := IF(@has = 1, 'ALTER TABLE `channel_ai_draft` DROP COLUMN `credits`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_report' AND COLUMN_NAME = 'credits');
SET @ddl := IF(@has = 1, 'ALTER TABLE `channel_ai_report` DROP COLUMN `credits`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_assistant_message' AND COLUMN_NAME = 'credits');
SET @ddl := IF(@has = 1, 'ALTER TABLE `channel_assistant_message` DROP COLUMN `credits`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_reactivate_item' AND COLUMN_NAME = 'credits');
SET @ddl := IF(@has = 1, 'ALTER TABLE `channel_reactivate_item` DROP COLUMN `credits`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_research' AND COLUMN_NAME = 'credits');
SET @ddl := IF(@has = 1, 'ALTER TABLE `channel_research` DROP COLUMN `credits`', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 已有数据库里还要清掉这些残留：菜单、支付应用、通知模板、权限
DELETE FROM `system_menu` WHERE `id` IN (20506, 22301, 22302, 22303);
DELETE FROM `pay_app` WHERE `app_key` = 'cgch-credit';
DELETE FROM `system_notify_template` WHERE `code` = 'cgch_credit_low';
DELETE FROM `system_role_menu` WHERE `menu_id` IN (20506, 22301, 22302, 22303);
