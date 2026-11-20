-- 038：接入 Unipile（替换 Evolution 的第一步）
--
-- 背景：Evolution 需要自己部署容器；Unipile 是托管接入，WhatsApp 走「关联设备」（扫码/配对码），
-- 不需要 Meta 开发者账号。多租户隔离用 Unipile 的 Scope + 每个公司专属的 Key。
--
-- 可重复执行（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，这里查 information_schema 后再执行）。

CREATE TABLE IF NOT EXISTS `channel_unipile_credential` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope_id` varchar(64) NOT NULL COMMENT 'Unipile 隔离边界（Scope）编号',
  `api_key` varchar(512) NOT NULL COMMENT '该 Scope 专属的 API Key（加密）',
  `api_key_mask` varchar(64) NULL COMMENT '脱敏后的 Key，展示用',
  `status` varchar(16) NOT NULL DEFAULT 'active' COMMENT '状态：active / disabled',
  `tenant_id` bigint NOT NULL COMMENT '公司编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unipile_scope` (`scope_id`),
  KEY `idx_unipile_tenant` (`tenant_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unipile 租户凭证（每家公司一条）';

-- 会话记下 Unipile 的 chat_id，回复时要用
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = DATABASE()
                    AND TABLE_NAME = 'channel_wa_conversation'
                    AND COLUMN_NAME = 'provider_chat_id');
SET @ddl := IF(@has_col = 0,
    'ALTER TABLE `channel_wa_conversation` ADD COLUMN `provider_chat_id` varchar(128) NULL COMMENT ''Unipile 会话编号'' AFTER `wa_id`',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
