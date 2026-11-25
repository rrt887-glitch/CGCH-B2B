-- 047：会话唯一性
--
-- 现象：同一个客户出现两个会话窗口（WhatsApp、Instagram、Messenger 都有）。
-- 原因：channel_wa_conversation 没有唯一约束，而「先查再插」在并发 webhook、或多个入口用不同 id 形式时会各插一条。
-- 做法：先把已有重复合并（消息/事件/未读并入最早那条，删掉多余），再加唯一索引兜底；
--      逻辑删除的行用生成列错开，避免「删过再建」撞唯一索引。
--
-- 可重复执行。

SET NAMES utf8mb4;

-- ---------- 1. 合并现有重复（按 租户 + 号码 + 客户编号） ----------
DROP TEMPORARY TABLE IF EXISTS `tmp_conv_dup`;
CREATE TEMPORARY TABLE `tmp_conv_dup` (
  `tenant_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `wa_id` varchar(255) NOT NULL,
  `keep_id` bigint NOT NULL,
  PRIMARY KEY (`tenant_id`, `account_id`, `wa_id`)
) ENGINE = InnoDB;

INSERT INTO `tmp_conv_dup` (`tenant_id`, `account_id`, `wa_id`, `keep_id`)
SELECT `tenant_id`, `account_id`, `wa_id`, MIN(`id`)
FROM `channel_wa_conversation`
WHERE `deleted` = b'0' AND `account_id` IS NOT NULL AND `wa_id` IS NOT NULL AND `wa_id` <> ''
GROUP BY `tenant_id`, `account_id`, `wa_id`
HAVING COUNT(*) > 1;

-- 消息改挂到保留的会话
UPDATE `channel_wa_message` m
JOIN `channel_wa_conversation` c ON c.`id` = m.`conversation_id`
JOIN `tmp_conv_dup` k ON k.`tenant_id` = c.`tenant_id` AND k.`account_id` = c.`account_id` AND k.`wa_id` = c.`wa_id`
SET m.`conversation_id` = k.`keep_id`
WHERE c.`id` <> k.`keep_id`;

-- 会话事件改挂
UPDATE `channel_wa_conversation_event` e
JOIN `channel_wa_conversation` c ON c.`id` = e.`conversation_id`
JOIN `tmp_conv_dup` k ON k.`tenant_id` = c.`tenant_id` AND k.`account_id` = c.`account_id` AND k.`wa_id` = c.`wa_id`
SET e.`conversation_id` = k.`keep_id`
WHERE c.`id` <> k.`keep_id`;

-- 未读数与最新消息时间合并到保留的会话
UPDATE `channel_wa_conversation` c
JOIN `tmp_conv_dup` k ON k.`keep_id` = c.`id`
JOIN (
  SELECT `tenant_id`, `account_id`, `wa_id`,
         SUM(COALESCE(`unread_count`, 0)) AS `total_unread`,
         MAX(`last_message_time`) AS `latest_time`,
         MAX(`last_inbound_time`) AS `latest_inbound`
  FROM `channel_wa_conversation`
  WHERE `deleted` = b'0' AND `account_id` IS NOT NULL AND `wa_id` IS NOT NULL AND `wa_id` <> ''
  GROUP BY `tenant_id`, `account_id`, `wa_id`
) s ON s.`tenant_id` = k.`tenant_id` AND s.`account_id` = k.`account_id` AND s.`wa_id` = k.`wa_id`
SET c.`unread_count` = s.`total_unread`,
    c.`last_message_time` = GREATEST(COALESCE(c.`last_message_time`, s.`latest_time`), COALESCE(s.`latest_time`, c.`last_message_time`)),
    c.`last_inbound_time` = GREATEST(COALESCE(c.`last_inbound_time`, s.`latest_inbound`), COALESCE(s.`latest_inbound`, c.`last_inbound_time`));

-- 物理删除多余的会话（逻辑删除会继续占用唯一索引）
DELETE c FROM `channel_wa_conversation` c
JOIN `tmp_conv_dup` k ON k.`tenant_id` = c.`tenant_id` AND k.`account_id` = c.`account_id` AND k.`wa_id` = c.`wa_id`
WHERE c.`id` <> k.`keep_id`;

DROP TEMPORARY TABLE IF EXISTS `tmp_conv_dup`;

-- ---------- 2. 唯一索引 ----------
-- 生成列：未删除的行固定 0（于是三元组唯一），已删除的行用自身 id 错开（允许删了再建）
SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_wa_conversation` ADD COLUMN `active_key` bigint GENERATED ALWAYS AS (IF(`deleted` = b''0'', 0, NULL)) STORED COMMENT ''唯一索引辅助列：删除行置 NULL，唯一索引允许多个 NULL''',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'active_key');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `channel_wa_conversation` ADD UNIQUE KEY `uk_conv_tenant_account_waid` (`tenant_id`, `account_id`, `wa_id`, `active_key`)',
    'SELECT 1')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND INDEX_NAME = 'uk_conv_tenant_account_waid');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
