-- 扫码接入的号码记录最近一次同步聊天记录的结果，渠道页显示导入了多少条（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `history_sync_time` datetime NULL COMMENT ''扫码接入：最近一次同步聊天记录的时间''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'history_sync_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `history_fetched` int NULL COMMENT ''最近一次同步：Evolution 里保存的记录数''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'history_fetched');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `history_imported` int NULL COMMENT ''最近一次同步：新导入的消息数''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'history_imported');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `history_no_number` int NULL COMMENT ''最近一次同步：来自隐藏号码联系人、暂时无法导入的消息数''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'history_no_number');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `history_sync_error` varchar(255) NULL COMMENT ''最近一次同步失败的原因''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'history_sync_error');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
