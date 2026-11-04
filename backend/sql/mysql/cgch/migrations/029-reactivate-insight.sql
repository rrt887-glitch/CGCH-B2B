-- 客户唤醒升级：写信前先背调、写好后自动发送，以及 AI 写信前的分析（客户现状、痛点、推荐产品）（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_reactivate_task` ADD COLUMN `auto_research` bit(1) NULL DEFAULT b''0'' COMMENT ''写信前先背调：没有 90 天内背调报告的客户先做一次背调''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_reactivate_task' AND COLUMN_NAME = 'auto_research');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_reactivate_task` ADD COLUMN `auto_send` bit(1) NULL DEFAULT b''0'' COMMENT ''写好后自动发送，不需要逐条确认''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_reactivate_task' AND COLUMN_NAME = 'auto_send');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_reactivate_item` ADD COLUMN `insight` text NULL COMMENT ''AI 写信前的分析（JSON）：客户现状、痛点、推荐产品、切入角度、依据的背调编号''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_reactivate_item' AND COLUMN_NAME = 'insight');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
