-- 044：背调支持 Facebook / LinkedIn 主页链接（很多客户没有公司名和官网，只有社交主页）
-- 可重复执行。

SET NAMES utf8mb4;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_research` ADD COLUMN `facebook_url` varchar(500) NULL COMMENT ''客户 Facebook 主页'' AFTER `website`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_research' AND COLUMN_NAME = 'facebook_url');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_research` ADD COLUMN `linkedin_url` varchar(500) NULL COMMENT ''客户 LinkedIn 主页'' AFTER `facebook_url`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_research' AND COLUMN_NAME = 'linkedin_url');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
