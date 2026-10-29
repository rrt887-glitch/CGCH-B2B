-- 按渠道账号分配接待成员：新客户在接待成员之间轮流分配（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `assign_user_ids` varchar(255) NULL COMMENT ''接待成员：逗号分隔的用户编号；这个号码上的新客户轮流分给他们，为空时由号码所属的业务员接待''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'assign_user_ids');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_account` ADD COLUMN `assign_cursor` int NULL DEFAULT 0 COMMENT ''轮流分配的游标：下一个新客户分给第几个接待成员''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_account' AND COLUMN_NAME = 'assign_cursor');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
