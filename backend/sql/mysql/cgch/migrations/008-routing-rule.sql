-- 消息分流规则（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `channel_routing_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '规则名称',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用',
  `sort` int NULL COMMENT '排序，越小越优先',
  `sources` mediumtext NULL COMMENT '消息来源渠道，逗号分隔，为空表示全部渠道',
  `country_op` varchar(255) NULL COMMENT '国家条件：in 属于、not_in 不属于；为空表示不限国家',
  `countries` varchar(255) NULL COMMENT '国家二字码，逗号分隔',
  `assignee_ids` varchar(2000) NULL COMMENT '负责人编号列表（多人时轮流分配）',
  `next_index` int NULL COMMENT '轮流分配的下一位（负责人列表中的下标）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '消息分流规则：新询盘来自哪些渠道、满足哪些条件时，分配给哪些业务员（多人时轮流分配）';
