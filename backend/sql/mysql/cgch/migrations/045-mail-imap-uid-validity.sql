-- 同一个 IMAP UID 在收件箱更换 UIDVALIDITY 后可以被重新分配。
SET NAMES utf8mb4;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_mail_message` ADD COLUMN `imap_uid_validity` bigint NULL COMMENT ''该 IMAP UID 所属的收件箱代次'' AFTER `imap_uid`',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_mail_message' AND COLUMN_NAME = 'imap_uid_validity');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `channel_mail_message` m
JOIN `channel_mail_account` a ON a.`id` = m.`account_id` AND a.`tenant_id` = m.`tenant_id`
SET m.`imap_uid_validity` = a.`uid_validity`
WHERE m.`imap_uid` IS NOT NULL AND m.`imap_uid_validity` IS NULL AND a.`uid_validity` > 0;
