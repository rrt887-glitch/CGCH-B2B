-- 会话记录客户头像（从 WhatsApp 联系人资料下载后存到本系统），会话列表和聊天顶部显示（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `avatar_url` varchar(512) NULL COMMENT ''客户头像：从 WhatsApp 联系人资料下载后存到本系统的文件地址''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'avatar_url');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `avatar_time` datetime NULL COMMENT ''最近一次获取头像的时间''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'avatar_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `avatar_key` varchar(64) NULL COMMENT ''头像来源标识，头像没换就不重复下载''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'avatar_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
