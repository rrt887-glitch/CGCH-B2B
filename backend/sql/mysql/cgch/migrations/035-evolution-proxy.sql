-- WhatsApp 扫码接入的出口代理：每家客户一个出口 IP，一家群发被风控时不牵连同服务器的其他客户。
-- 已有数据库执行一次，可重复执行；新装数据库由 00-channel-schema.sql 建好。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `channel_evolution_proxy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用：关闭时扫码号直接从服务器 IP 出去',
  `host` varchar(255) NULL COMMENT '代理主机',
  `port` int NULL COMMENT '代理端口',
  `protocol` varchar(16) NULL COMMENT '协议：http、https、socks4、socks5',
  `username` varchar(128) NULL COMMENT '代理账号',
  `password` varchar(512) NULL COMMENT '代理密码（加密）',
  `last_applied_time` datetime NULL COMMENT '最近一次设置到号码上的时间',
  `last_error` varchar(512) NULL COMMENT '最近一次设置失败的原因',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WhatsApp 扫码接入的出口代理，每个租户一条';
