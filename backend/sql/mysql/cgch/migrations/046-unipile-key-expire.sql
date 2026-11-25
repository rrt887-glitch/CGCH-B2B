-- 046：Unipile 租户 Key 记到期时间与 Key 编号，支持自动续期
-- 可重复执行。

SET NAMES utf8mb4;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_unipile_credential` ADD COLUMN `key_expire_time` datetime NULL COMMENT ''租户 Key 到期时间'' AFTER `api_key_mask`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_unipile_credential' AND COLUMN_NAME = 'key_expire_time');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_unipile_credential` ADD COLUMN `key_id` varchar(64) NULL COMMENT ''Unipile Key 编号（续期后删旧 Key 用）'' AFTER `key_expire_time`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_unipile_credential' AND COLUMN_NAME = 'key_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 老数据没有到期时间：按「300 天后到期」回填，让续期任务在 30 天警戒线之前先跑一轮
UPDATE `channel_unipile_credential`
SET `key_expire_time` = DATE_ADD(NOW(), INTERVAL 300 DAY)
WHERE `key_expire_time` IS NULL AND `scope_id` IS NOT NULL AND `scope_id` <> 'global' AND `deleted` = b'0';
