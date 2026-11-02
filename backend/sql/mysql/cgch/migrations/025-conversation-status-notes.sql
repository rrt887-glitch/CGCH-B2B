-- 会话状态（进行中 / 待处理 / 已解决）、优先级，以及内部备注与会话动态表（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `status` varchar(16) NULL COMMENT ''会话状态：open 进行中（空也算）、pending 待处理、resolved 已解决''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'status');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `priority` int NULL COMMENT ''优先级：0 无、1 低、2 中、3 高、4 紧急''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'priority');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS `channel_wa_conversation_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `conversation_id` bigint NOT NULL COMMENT '会话编号',
  `kind` varchar(16) NOT NULL COMMENT '类型：note 内部备注（客户看不到）、activity 会话动态（指派、状态、优先级变化）',
  `content` varchar(2000) NULL COMMENT '内容',
  `user_id` bigint NULL COMMENT '操作人；为空表示系统',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_conversation` (`conversation_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会话内部备注与动态（只给团队看）';
