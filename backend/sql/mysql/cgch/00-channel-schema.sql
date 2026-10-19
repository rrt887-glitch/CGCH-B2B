-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `channel_mail_account`;
CREATE TABLE `channel_mail_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '所属用户编号',
  `provider` varchar(255) NULL COMMENT '邮箱服务商：gmail、outlook、qq、exmail、163、126、263、aliyun、custom',
  `email` varchar(255) NULL COMMENT '邮箱地址',
  `display_name` varchar(255) NULL COMMENT '发件人显示名称',
  `imap_host` varchar(255) NULL COMMENT 'IMAP 服务器',
  `imap_port` int NULL COMMENT 'IMAP 端口',
  `imap_ssl` bit(1) NULL DEFAULT b'0' COMMENT 'IMAP 是否使用 SSL',
  `smtp_host` varchar(255) NULL COMMENT 'SMTP 服务器',
  `smtp_port` int NULL COMMENT 'SMTP 端口',
  `smtp_ssl` bit(1) NULL DEFAULT b'0' COMMENT 'SMTP 是否使用 SSL（465）；否则尝试 STARTTLS',
  `username` varchar(255) NULL COMMENT '登录用户名，一般就是邮箱地址',
  `password` varchar(255) NULL COMMENT '登录密码或授权码，加密存储',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用自动收信',
  `last_sync_time` datetime NULL COMMENT '最近一次同步时间',
  `last_sync_uid` bigint NULL COMMENT '收件箱已同步到的最大 IMAP UID',
  `uid_validity` bigint NULL COMMENT '收件箱 UIDVALIDITY，变化时需要重新同步',
  `last_sync_error` mediumtext NULL COMMENT '最近一次同步失败的原因，成功时清空',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '业务员绑定的邮箱账号（IMAP 收信 + SMTP 发信）';

DROP TABLE IF EXISTS `channel_mail_message`;
CREATE TABLE `channel_mail_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `account_id` bigint NULL COMMENT '邮箱账号编号',
  `folder` varchar(255) NULL COMMENT '文件夹：INBOX、SENT、DRAFT、TRASH',
  `direction` int NULL COMMENT '方向：1 收到，2 发出',
  `imap_uid` bigint NULL COMMENT '收件箱中的 IMAP UID，发出的邮件为空',
  `message_id` varchar(255) NULL COMMENT '邮件头 Message-ID',
  `in_reply_to` varchar(255) NULL COMMENT '邮件头 In-Reply-To',
  `thread_key` varchar(255) NULL COMMENT '会话标识：同一来回往复的邮件相同',
  `from_address` varchar(255) NULL COMMENT '发件人地址',
  `from_name` varchar(255) NULL COMMENT '发件人名称',
  `to_addresses` mediumtext NULL COMMENT '收件人，逗号分隔',
  `cc_addresses` mediumtext NULL COMMENT '抄送，逗号分隔',
  `subject` varchar(255) NULL COMMENT '主题',
  `text_body` mediumtext NULL COMMENT '纯文本正文',
  `html_body` mediumtext NULL COMMENT 'HTML 正文',
  `snippet` varchar(255) NULL COMMENT '摘要，列表展示用',
  `sent_time` datetime NULL COMMENT '发送时间',
  `read_status` bit(1) NULL DEFAULT b'0' COMMENT '是否已读',
  `replied` bit(1) NULL DEFAULT b'0' COMMENT '是否已回复（仅收到的邮件）',
  `customer_id` bigint NULL COMMENT '关联的 CRM 客户编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '邮件（收到的与发出的）';
