-- WhatsApp 扫码接入（Evolution API）：账号连接状态（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `conn_state` varchar(16) NULL COMMENT ''扫码接入的连接状态：open 已连接、connecting 连接中、close 已断开（需重新扫码）；其他接入方式为空'' AFTER `last_webhook_time`',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'conn_state');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
