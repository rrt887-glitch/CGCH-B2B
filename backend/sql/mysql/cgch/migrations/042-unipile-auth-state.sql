-- 042：Unipile 授权状态（谁在发起连接）
--
-- 之前 state 直接放「租户:渠道」，回调时无法知道是哪个成员发起的授权，
-- 导致 Unipile 接入的渠道账号没有归属人（只有管理员能看到）。
-- 这里用一次性 token 记录发起人，回调时按 token 取回租户 / 成员 / 渠道，顺带防伪造。
--
-- 可重复执行。

CREATE TABLE IF NOT EXISTS `channel_unipile_auth_state` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `token` varchar(64) NOT NULL COMMENT '一次性授权令牌（放在 state 里）',
  `tenant_id` bigint NOT NULL COMMENT '发起授权的租户',
  `user_id` bigint NOT NULL COMMENT '发起授权的成员（绑定的账号归他）',
  `provider` varchar(32) NOT NULL COMMENT '渠道：whatsapp / linkedin / instagram / facebook / telegram',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `used` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否已使用（一次性）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_unipile_state_token` (`token`),
  KEY `idx_unipile_state_expire` (`expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Unipile 托管授权的发起记录';
