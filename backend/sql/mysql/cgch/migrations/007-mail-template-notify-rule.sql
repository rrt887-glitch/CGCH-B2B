-- 邮件模板、通知规则（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `channel_mail_template` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '模板名称',
  `category` varchar(255) NULL COMMENT '分类：promo 推广邮件、notice 通知邮件、transaction 事务邮件',
  `visibility` varchar(255) NULL COMMENT '可见范围：all 全员、self 仅自己',
  `owner_user_id` bigint NULL COMMENT '创建人',
  `subject` varchar(255) NULL COMMENT '邮件标题',
  `html` mediumtext NULL COMMENT '邮件正文（HTML）',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用',
  `defaulted` bit(1) NULL DEFAULT b'0' COMMENT '是否为该分类的默认模板',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '邮件模板：写邮件时一键插入标题与正文';

CREATE TABLE IF NOT EXISTS `channel_notify_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '所属用户',
  `type` varchar(255) NULL COMMENT '消息类型：handoff 转人工、takeover AI 接手、new_lead 新询盘、reactivate_reply 唤醒客户回复、credit_low 智点不足',
  `channel` varchar(255) NULL COMMENT '通知方式：site 站内信、email 邮件',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否开启',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '通知规则：每个账号自己设置哪类消息用什么方式通知；没有设置规则的账号默认以站内信接收全部通知';
