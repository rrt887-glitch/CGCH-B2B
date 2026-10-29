-- 客户阶段：新询盘 → 报价 → 样品 → 谈判 → 成交 → 复购（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @added = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crm_customer' AND COLUMN_NAME = 'stage');
SET @ddl = IF(@added,
  'ALTER TABLE `crm_customer` ADD COLUMN `stage` int NULL DEFAULT 1 COMMENT ''客户阶段，字典 crm_customer_stage：1 新询盘、2 报价、3 样品、4 谈判、5 成交、6 复购''',
  'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
-- 第一次加列时按已有数据推断：已成交的客户记为「成交」，其余为「新询盘」
SET @fill = IF(@added, 'UPDATE `crm_customer` SET `stage` = IF(`deal_status` = b''1'', 5, 1)', 'SELECT 1');
PREPARE stmt FROM @fill;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
DROP PROCEDURE IF EXISTS cgch_add_index;
DELIMITER $$
CREATE PROCEDURE cgch_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN c1 VARCHAR(64), IN c2 VARCHAR(64), IN c3 VARCHAR(64))
BEGIN
  DECLARE need INT DEFAULT 1 + (c2 IS NOT NULL) + (c3 IS NOT NULL);
  DECLARE have INT;
  SELECT COUNT(*) INTO have FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME IN (c1, IFNULL(c2, c1), IFNULL(c3, c1));
  IF have = need AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = i) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD INDEX `', i, '` (`', c1, '`',
                      IF(c2 IS NULL, '', CONCAT(', `', c2, '`')), IF(c3 IS NULL, '', CONCAT(', `', c3, '`')), ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL cgch_add_index('crm_customer', 'idx_stage', 'stage', NULL, NULL);
DROP PROCEDURE cgch_add_index;
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT '客户阶段', 'crm_customer_stage', 0, '客户从询盘到复购的阶段，可在这里增改', '1', '1', b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_customer_stage' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
SELECT s.sort, s.label, s.value, 'crm_customer_stage', 0, s.color, '', '', '1', '1', b'0' FROM (
  SELECT 1 AS sort, '新询盘' AS label, '1' AS value, 'processing' AS color UNION ALL
  SELECT 2, '报价', '2', 'warning' UNION ALL
  SELECT 3, '样品', '3', 'warning' UNION ALL
  SELECT 4, '谈判', '4', 'danger' UNION ALL
  SELECT 5, '成交', '5', 'success' UNION ALL
  SELECT 6, '复购', '6', 'success') s
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d WHERE d.`dict_type` = 'crm_customer_stage' AND d.`value` = s.value AND d.`deleted` = b'0');
