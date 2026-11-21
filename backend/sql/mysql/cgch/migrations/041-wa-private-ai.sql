-- 为 WhatsApp 私聊增加独立的 AI 接待开关，保留已有会话的接待规则。
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `private_ai_enabled` bit(1) NOT NULL DEFAULT b''1'' COMMENT ''WhatsApp 私聊是否允许 AI 自动接待（默认允许，仍按 AI 接待设置）''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'private_ai_enabled');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
