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
  `password` varchar(1024) NULL COMMENT '登录密码或授权码，加密存储',
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

DROP TABLE IF EXISTS `channel_wa_account`;
CREATE TABLE `channel_wa_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '负责该号码的用户编号',
  `name` varchar(255) NULL COMMENT '显示名称，例如「销售一部 WhatsApp」',
  `phone_number_id` varchar(255) NULL COMMENT 'Meta 的 Phone number ID',
  `waba_id` varchar(255) NULL COMMENT 'WhatsApp Business Account ID，用于查询消息模板',
  `display_phone` varchar(255) NULL COMMENT '号码，例如 +86 138 0000 0000，展示用',
  `access_token` varchar(1024) NULL COMMENT '访问令牌（建议使用系统用户的永久令牌），加密存储',
  `app_secret` varchar(1024) NULL COMMENT 'Meta 应用密钥，用于校验 Webhook 签名，加密存储',
  `verify_token` varchar(255) NULL COMMENT 'Webhook 验证令牌，在 Meta 后台配置回调地址时填写',
  `api_base` varchar(255) NULL COMMENT 'Graph API 地址，默认 https://graph.facebook.com',
  `api_version` varchar(255) NULL COMMENT 'Graph API 版本，例如 v24.0',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用',
  `last_webhook_time` datetime NULL COMMENT '最近一次收到 Webhook 的时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WhatsApp Business 号码（Meta 官方 Cloud API）';

DROP TABLE IF EXISTS `channel_wa_conversation`;
CREATE TABLE `channel_wa_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `account_id` bigint NULL COMMENT '业务号码编号',
  `wa_id` varchar(255) NULL COMMENT '客户的 WhatsApp 号码（纯数字，含国家码）',
  `profile_name` varchar(255) NULL COMMENT '客户在 WhatsApp 上的昵称',
  `customer_id` bigint NULL COMMENT '关联的 CRM 客户编号',
  `last_message_time` datetime NULL COMMENT '最后一条消息时间',
  `last_inbound_time` datetime NULL COMMENT '客户最后一次发来消息的时间，用于判断 24 小时客服窗口',
  `last_message_preview` varchar(255) NULL COMMENT '最后一条消息摘要',
  `unread_count` int NULL COMMENT '未读条数',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WhatsApp 会话：一个业务号码与一位客户之间的聊天';

DROP TABLE IF EXISTS `channel_wa_message`;
CREATE TABLE `channel_wa_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `account_id` bigint NULL COMMENT '业务号码编号',
  `conversation_id` bigint NULL COMMENT '会话编号',
  `direction` int NULL COMMENT '方向：1 收到，2 发出',
  `wa_message_id` varchar(255) NULL COMMENT 'Meta 消息编号（wamid）',
  `type` varchar(255) NULL COMMENT '类型：text、image、document、audio、video、sticker、location、template、interactive 等',
  `text_body` mediumtext NULL COMMENT '文本内容、媒体说明或模板预览',
  `media_id` varchar(255) NULL COMMENT '媒体编号，可通过 Graph API 下载',
  `mime_type` varchar(255) NULL COMMENT '媒体类型',
  `file_name` varchar(255) NULL COMMENT '文件名（document 类型）',
  `template_name` varchar(255) NULL COMMENT '模板名称（template 类型）',
  `status` varchar(255) NULL COMMENT '状态：received、sent、delivered、read、failed',
  `error_message` varchar(255) NULL COMMENT '发送失败原因',
  `message_time` datetime NULL COMMENT '消息时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WhatsApp 消息';
