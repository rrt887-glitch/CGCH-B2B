-- WhatsApp 群聊：导入群聊会话，群聊的 AI 自动回复按群手动开启；群消息记录发送人（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `ai_enabled` bit(1) NULL DEFAULT b''0'' COMMENT ''WhatsApp 群聊是否由 AI 自动回复（默认关闭，手动开启）；私聊不使用''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'ai_enabled');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_message` ADD COLUMN `sender_wa_id` varchar(64) NULL COMMENT ''群聊里发这条消息的成员（号码或隐藏编号@lid）''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_message' AND COLUMN_NAME = 'sender_wa_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_message` ADD COLUMN `sender_name` varchar(100) NULL COMMENT ''群聊里发这条消息的成员昵称''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_message' AND COLUMN_NAME = 'sender_name');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
