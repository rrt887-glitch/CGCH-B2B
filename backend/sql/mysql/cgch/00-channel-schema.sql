-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `channel_ai_config`;
CREATE TABLE `channel_ai_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `agent_name` varchar(255) NULL COMMENT '数字员工名称，回复时的自称',
  `personality` varchar(255) NULL COMMENT '个性：professional 专业严谨、concise 高效简洁、friendly 温和友好、proactive 积极推进、calm 冷静专业',
  `default_language` varchar(255) NULL COMMENT '默认答复语种，例如 English；无法从客户消息判断语种时使用',
  `llm_base_url` varchar(255) NULL COMMENT '大模型接口地址（OpenAI 兼容），例如 https://api.deepseek.com/v1',
  `llm_api_key` varchar(1024) NULL COMMENT '大模型 API Key，加密存储',
  `llm_model` varchar(255) NULL COMMENT '模型名称，例如 deepseek-chat、qwen-plus、gpt-4o-mini',
  `embedding_model` varchar(255) NULL COMMENT '向量模型（OpenAI 兼容 /embeddings，与对话模型共用接口地址和 Key）；为空时知识库按关键词检索',
  `mail_mode` varchar(255) NULL COMMENT '邮件回复模式：off、draft、auto',
  `wa_mode` varchar(255) NULL COMMENT 'WhatsApp 回复模式：off、draft、auto',
  `mail_signature` varchar(1024) NULL COMMENT '邮件签名，追加在 AI 回复末尾',
  `human_pause_hours` int NULL COMMENT '业务员人工回复后，AI 暂停自动回复的小时数',
  `extra_instructions` varchar(1024) NULL COMMENT '补充要求，例如「报价统一用美元，不承诺交期」',
  `lead_auto_create` bit(1) NULL DEFAULT b'0' COMMENT '新询盘自动建 CRM 线索',
  `lead_ai_extract` bit(1) NULL DEFAULT b'0' COMMENT '建档时用 AI 从询盘中提取公司、国家、需求产品等（消耗智点）',
  `report_auto` bit(1) NULL DEFAULT b'0' COMMENT '是否每天自动生成 AI 工作报告（周一另生成上周周报）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 数字员工配置，每个租户一条';

DROP TABLE IF EXISTS `channel_ai_draft`;
CREATE TABLE `channel_ai_draft` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `channel` varchar(255) NULL COMMENT '渠道：mail、whatsapp',
  `ref_id` bigint NULL COMMENT '关联对象：邮件为收到的邮件编号，WhatsApp 为会话编号',
  `content` mediumtext NULL COMMENT '回复内容',
  `status` varchar(255) NULL COMMENT '状态：pending 待处理、sent 已发送、discarded 已放弃',
  `reason` varchar(255) NULL COMMENT '未自动发送的原因，见 REASON_*',
  `reason_detail` varchar(1024) NULL COMMENT '原因补充说明，例如命中的违禁词',
  `prompt_tokens` int NULL COMMENT '本次消耗的输入 token',
  `completion_tokens` int NULL COMMENT '本次消耗的输出 token',
  `credits` bigint NULL COMMENT '本次扣除的智点',
  `model` varchar(255) NULL COMMENT '使用的模型',
  `knowledge_hits` int NULL COMMENT '本次回复命中的知识条目数（不含企业介绍）；为 0 表示知识库里没有相关资料',
  `question` varchar(1024) NULL COMMENT '客户本次的问题（截取），用于工作报告里的「知识库缺口」',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 生成的回复：起草模式下的草稿、自动回复被拦下的草稿，以及已自动发出的记录';

DROP TABLE IF EXISTS `channel_ai_forbidden_word`;
CREATE TABLE `channel_ai_forbidden_word` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `word` varchar(255) NULL COMMENT '词语，不区分大小写',
  `remark` varchar(1024) NULL COMMENT '说明，例如「不能承诺质量保证」',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '违禁词：AI 回复中出现时不会自动发出，转为草稿等人工确认';

DROP TABLE IF EXISTS `channel_ai_knowledge`;
CREATE TABLE `channel_ai_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` varchar(255) NULL COMMENT '类型：company 企业介绍、product 产品资料、faq 常见问答',
  `title` varchar(255) NULL COMMENT '标题；常见问答为问题',
  `content` mediumtext NULL COMMENT '内容；常见问答为答案',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用',
  `sort` int NULL COMMENT '排序',
  `embedding` mediumtext NULL COMMENT '标题+内容的向量（JSON 数组），用于语义检索；不对外返回',
  `embedding_model` varchar(255) NULL COMMENT '生成向量所用的模型；与当前配置的向量模型不一致时视为未建索引',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库条目：企业介绍、产品资料、常见问答';

DROP TABLE IF EXISTS `channel_ai_report`;
CREATE TABLE `channel_ai_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `period_type` varchar(255) NULL COMMENT '类型：daily 日报、weekly 周报',
  `period_start` date NULL COMMENT '统计开始日期（含）',
  `period_end` date NULL COMMENT '统计结束日期（含）',
  `stats` mediumtext NULL COMMENT '统计数据（JSON）：本期与上期汇总、转人工清单、知识库缺口',
  `summary` mediumtext NULL COMMENT '文字总结',
  `ai_generated` bit(1) NULL DEFAULT b'0' COMMENT '总结是否由大模型撰写；否则为按模板生成',
  `credits` bigint NULL COMMENT '撰写总结消耗的智点',
  `generate_mode` varchar(255) NULL COMMENT '生成方式：auto 定时、manual 手动（trigger 是 MySQL 保留字，故用 generate_mode）',
  `user_id` bigint NULL COMMENT '手动生成的操作人',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 工作报告（日报 / 周报）';

DROP TABLE IF EXISTS `channel_assistant_chat`;
CREATE TABLE `channel_assistant_chat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '所属用户',
  `title` varchar(255) NULL COMMENT '标题（取第一句提问）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话助手的一次会话（每个业务员只能看到自己的会话）';

DROP TABLE IF EXISTS `channel_assistant_message`;
CREATE TABLE `channel_assistant_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `chat_id` bigint NULL COMMENT '会话编号',
  `role` varchar(255) NULL COMMENT '角色：user、assistant',
  `content` mediumtext NULL COMMENT '内容',
  `steps` mediumtext NULL COMMENT '本轮调用过的工具（JSON 数组：工具名、参数），用于展示「查了什么」',
  `credits` bigint NULL COMMENT '消耗的智点',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '对话助手的一条消息';

DROP TABLE IF EXISTS `channel_credit_account`;
CREATE TABLE `channel_credit_account` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `balance` bigint NULL COMMENT '当前余额（智点）；扣费在调用之后，可能短暂为负',
  `total_in` bigint NULL COMMENT '累计获得',
  `total_out` bigint NULL COMMENT '累计消耗',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '租户的智点余额，每个租户一条';

DROP TABLE IF EXISTS `channel_credit_ledger`;
CREATE TABLE `channel_credit_ledger` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` varchar(255) NULL COMMENT '类型：recharge 充值、consume 消耗、adjust 调整',
  `amount` bigint NULL COMMENT '变动数量，获得为正、消耗为负',
  `balance_after` bigint NULL COMMENT '变动后余额',
  `channel` varchar(255) NULL COMMENT '产生消耗的渠道：mail、whatsapp',
  `ref_id` bigint NULL COMMENT '关联对象：AI 回复记录编号',
  `user_id` bigint NULL COMMENT '相关业务员（手动起草为操作人，自动回复为号码/邮箱所属人）',
  `tokens` int NULL COMMENT '本次消耗的 token 数',
  `remark` varchar(1024) NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '智点流水';

DROP TABLE IF EXISTS `channel_credit_order`;
CREATE TABLE `channel_credit_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '下单人',
  `package_id` bigint NULL COMMENT '套餐编号',
  `package_name` varchar(255) NULL COMMENT '套餐名',
  `credits` bigint NULL COMMENT '到账智点',
  `price` int NULL COMMENT '金额，单位：分',
  `pay_order_id` bigint NULL COMMENT '支付单编号',
  `pay_status` bit(1) NULL DEFAULT b'0' COMMENT '是否已支付',
  `pay_time` datetime NULL COMMENT '支付时间',
  `pay_channel_code` varchar(255) NULL COMMENT '支付渠道',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '智点在线充值订单';

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

DROP TABLE IF EXISTS `channel_mail_attachment`;
CREATE TABLE `channel_mail_attachment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `message_id` bigint NULL COMMENT '所属邮件编号；待发送的附件为空',
  `user_id` bigint NULL COMMENT '上传者用户编号（待发送附件的归属校验）',
  `file_name` varchar(255) NULL COMMENT '文件名',
  `content_type` varchar(255) NULL COMMENT 'MIME 类型',
  `file_size` bigint NULL COMMENT '文件大小，字节',
  `file_url` varchar(1024) NULL COMMENT '文件访问地址',
  `content_id` varchar(255) NULL COMMENT '内嵌图片的 Content-ID（正文中以 cid: 引用），普通附件为空',
  `inlined` bit(1) NULL DEFAULT b'0' COMMENT '是否正文内嵌图片',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '邮件附件。收到的邮件附件在同步时保存；发信时先上传（messageId 为空），发送成功后挂到已发送邮件上';

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
  `clue_id` bigint NULL COMMENT '关联的 CRM 线索编号（未成为客户的询盘）',
  `has_attachments` bit(1) NULL DEFAULT b'0' COMMENT '是否带附件（不含正文内嵌图片）',
  `ai_generated` bit(1) NULL DEFAULT b'0' COMMENT '是否由 AI 数字员工生成并发出',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '邮件（收到的与发出的）';

DROP TABLE IF EXISTS `channel_reactivate_item`;
CREATE TABLE `channel_reactivate_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NULL COMMENT '任务编号',
  `customer_id` bigint NULL COMMENT '客户编号',
  `customer_name` varchar(255) NULL COMMENT '客户名称',
  `owner_user_id` bigint NULL COMMENT '客户负责人',
  `last_contact_time` datetime NULL COMMENT '上次联系时间',
  `channel` varchar(255) NULL COMMENT '发送渠道：mail、whatsapp',
  `recipient` varchar(255) NULL COMMENT '收件人：邮箱或 WhatsApp 号码',
  `account_id` bigint NULL COMMENT '发送使用的邮箱或 WhatsApp 账号',
  `subject` varchar(255) NULL COMMENT '邮件主题',
  `content` mediumtext NULL COMMENT '正文；WhatsApp 为模板预览',
  `status` varchar(255) NULL COMMENT '状态：generating、draft、approved、sent、failed、skipped',
  `reason_detail` varchar(1024) NULL COMMENT '失败或跳过原因',
  `credits` bigint NULL COMMENT '生成消耗的智点',
  `sent_time` datetime NULL COMMENT '发送时间',
  `sent_ref_id` bigint NULL COMMENT '发出的邮件编号或 WhatsApp 消息编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户唤醒任务中的一位客户及其待发内容';

DROP TABLE IF EXISTS `channel_reactivate_task`;
CREATE TABLE `channel_reactivate_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '任务名称',
  `inactive_days` int NULL COMMENT '筛选条件：多少天未联系',
  `channel` varchar(255) NULL COMMENT '渠道偏好：auto 有邮箱发邮件否则 WhatsApp、mail 只发邮件、whatsapp 只发 WhatsApp',
  `instructions` varchar(255) NULL COMMENT '给 AI 的写作要求，例如介绍新品、节日问候',
  `wa_template_name` varchar(255) NULL COMMENT 'WhatsApp 模板名称（超过 24 小时只能发已审核的模板）',
  `wa_template_language` varchar(255) NULL COMMENT 'WhatsApp 模板语言',
  `status` varchar(255) NULL COMMENT '状态：generating、ready、sending、done',
  `user_id` bigint NULL COMMENT '创建人',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户唤醒任务：选一批久未联系的客户，由 AI 写跟进内容，业务员确认后批量发出';

DROP TABLE IF EXISTS `channel_research`;
CREATE TABLE `channel_research` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `company_name` varchar(255) NULL COMMENT '公司名称（输入或识别结果）',
  `website` varchar(255) NULL COMMENT '官网',
  `customer_id` bigint NULL COMMENT '关联的 CRM 客户',
  `clue_id` bigint NULL COMMENT '关联的 CRM 线索',
  `status` varchar(255) NULL COMMENT '状态：running、done、failed',
  `report` mediumtext NULL COMMENT '背调报告（JSON）',
  `sources` mediumtext NULL COMMENT '参考来源（JSON 数组）：网址、标题、摘要',
  `reason_detail` varchar(1024) NULL COMMENT '失败原因或提示',
  `credits` bigint NULL COMMENT '消耗的智点',
  `user_id` bigint NULL COMMENT '发起人',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '深度背调：根据客户公司官网的公开信息和我方往来记录，由 AI 整理成背调报告';

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
  `clue_id` bigint NULL COMMENT '关联的 CRM 线索编号（未成为客户的询盘）',
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
  `file_url` varchar(1024) NULL COMMENT '已保存到本地文件存储的地址；收到的媒体在首次查看时下载',
  `file_size` bigint NULL COMMENT '文件大小，字节',
  `template_name` varchar(255) NULL COMMENT '模板名称（template 类型）',
  `status` varchar(255) NULL COMMENT '状态：received、sent、delivered、read、failed',
  `error_message` varchar(255) NULL COMMENT '发送失败原因',
  `message_time` datetime NULL COMMENT '消息时间',
  `ai_generated` bit(1) NULL DEFAULT b'0' COMMENT '是否由 AI 数字员工生成并发出',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'WhatsApp 消息';

DROP TABLE IF EXISTS `channel_ai_deferred`;
CREATE TABLE `channel_ai_deferred` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `channel` varchar(255) NULL COMMENT '渠道：mail、whatsapp',
  `ref_id` bigint NULL COMMENT '邮件编号或 WhatsApp 会话编号',
  `message_id` bigint NULL COMMENT '触发的消息编号（WhatsApp 为消息编号，邮件同 refId）',
  `due_time` datetime NULL COMMENT '到期时间',
  `status` varchar(255) NULL COMMENT '状态：waiting、human_replied、ai_handled、superseded',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '等待 AI 接手的消息：工作时间内业务员优先，到点仍无人回复时由 AI 处理。存库以免服务重启丢失';

DROP TABLE IF EXISTS `channel_work_time`;
CREATE TABLE `channel_work_time` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用工作时间；关闭时按数字员工的回复模式全天处理',
  `timezone` varchar(255) NULL COMMENT '公司所在时区，例如 Asia/Shanghai',
  `schedule` mediumtext NULL COMMENT '每周安排（JSON 数组）：[{"day":1,"enabled":true,"ranges":"09:00-12:00,13:30-18:00"}, ...]，day 1=周一 … 7=周日',
  `holidays` mediumtext NULL COMMENT '节假日（JSON 数组，yyyy-MM-dd），当天全天休息',
  `extra_workdays` mediumtext NULL COMMENT '调休上班日（JSON 数组，yyyy-MM-dd），当天按周一的时间上班',
  `in_hours_policy` varchar(255) NULL COMMENT '工作时间内的处理方式：human_first、ai、draft',
  `takeover_minutes` int NULL COMMENT '业务员优先时，客户来信后等待多少分钟无人回复再由 AI 接手',
  `off_hours_policy` varchar(255) NULL COMMENT '非工作时间的处理方式：ai、draft',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '工作时间：决定 AI 在上班时间和下班时间分别怎么接待。每个租户一条';
