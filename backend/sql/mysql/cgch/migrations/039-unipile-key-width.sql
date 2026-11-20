-- 039：加宽 Unipile 凭证的 key 字段
--
-- 038 里 api_key 是 varchar(512)、api_key_mask 是 varchar(64)，
-- 实测插入报 Data too long for column 'api_key_mask'（加密后的值比预期长）。
-- 这里统一加宽，避免再因为长度失败。可重复执行。

SET @ddl := IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_unipile_credential' AND COLUMN_NAME = 'api_key') < 2048,
    'ALTER TABLE `channel_unipile_credential` MODIFY COLUMN `api_key` varchar(2048) NOT NULL COMMENT ''该 Scope 专属的 API Key（加密）''',
    'SELECT 1');
PREPARE s1 FROM @ddl;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @ddl2 := IF(
    (SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_unipile_credential' AND COLUMN_NAME = 'api_key_mask') < 2048,
    'ALTER TABLE `channel_unipile_credential` MODIFY COLUMN `api_key_mask` varchar(2048) NULL COMMENT ''脱敏后的 Key，展示用''',
    'SELECT 1');
PREPARE s2 FROM @ddl2;
EXECUTE s2;
DEALLOCATE PREPARE s2;
