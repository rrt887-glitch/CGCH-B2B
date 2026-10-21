-- CGCH：渠道消息（邮箱）菜单与按钮权限
-- 在 01-menu.sql 之后执行；可重复执行
SET NAMES utf8mb4;

-- 「邮件工作台」「渠道管理」从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/channel/mail/index' WHERE `id` = 20102;
UPDATE `system_menu` SET `component` = 'cgch/config/channels/index' WHERE `id` = 20501;

DELETE FROM `system_menu` WHERE `id` BETWEEN 22000 AND 22099;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22001, '邮箱查询', 'channel:mail-account:query',  3, 1, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22002, '邮箱绑定', 'channel:mail-account:create', 3, 2, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22003, '邮箱修改', 'channel:mail-account:update', 3, 3, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22004, '邮箱解绑', 'channel:mail-account:delete', 3, 4, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22011, '邮件查看', 'channel:mail-message:query',  3, 1, 20102, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22012, '发送邮件', 'channel:mail-message:send',   3, 2, 20102, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22013, '删除邮件', 'channel:mail-message:delete', 3, 3, 20102, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- WhatsApp：消息工作台、模板管理从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/channel/im/index' WHERE `id` = 20101;
UPDATE `system_menu` SET `component` = 'cgch/config/templates/index' WHERE `id` = 20503;

DELETE FROM `system_menu` WHERE `id` BETWEEN 22100 AND 22199;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22101, 'WhatsApp 号码查询', 'channel:wa-account:query',  3, 11, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22102, 'WhatsApp 号码绑定', 'channel:wa-account:create', 3, 12, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22103, 'WhatsApp 号码修改', 'channel:wa-account:update', 3, 13, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22104, 'WhatsApp 号码解绑', 'channel:wa-account:delete', 3, 14, 20501, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22111, 'WhatsApp 消息查看', 'channel:wa-message:query',  3, 1, 20101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22112, 'WhatsApp 发送消息', 'channel:wa-message:send',   3, 2, 20101, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 文件存储默认用数据库：上游默认的七牛示例配置没有可用密钥，会导致附件、媒体无法保存
-- 生产环境建议在「基础设施 → 文件配置」改为对象存储（S3/OSS/COS/MinIO），并把 domain 改成你的域名
UPDATE `infra_file_config` SET `master` = (`id` = 4);

-- AI 数字员工：数字员工、知识库、违禁词配置从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/agent/profile/index' WHERE `id` = 20401;
UPDATE `system_menu` SET `component` = 'cgch/agent/knowledge/index' WHERE `id` = 20402;
UPDATE `system_menu` SET `component` = 'cgch/agent/forbidden/index' WHERE `id` = 20405;

DELETE FROM `system_menu` WHERE `id` BETWEEN 22200 AND 22299;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22201, '数字员工配置', 'channel:ai:config',    3, 1, 20401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22202, 'AI 起草与草稿', 'channel:ai:draft',    3, 2, 20401, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22203, '知识库维护',   'channel:ai:knowledge', 3, 1, 20402, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22204, '违禁词维护',   'channel:ai:forbidden', 3, 1, 20405, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 智点：「智点消耗」从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/config/credits/index' WHERE `id` = 20506;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22300 AND 22399;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22301, '智点查询', 'channel:credit:query',    3, 1, 20506, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (22302, '智点充值', 'channel:credit:recharge', 3, 2, 20506, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 默认租户赠送 1000 智点用于试用
DELETE FROM `channel_credit_ledger` WHERE `tenant_id` = 1;
DELETE FROM `channel_credit_account` WHERE `tenant_id` = 1;
INSERT INTO `channel_credit_account` (`balance`, `total_in`, `total_out`, `creator`, `updater`, `tenant_id`) VALUES (1000, 1000, 0, '1', '1', 1);
INSERT INTO `channel_credit_ledger` (`type`, `amount`, `balance_after`, `user_id`, `remark`, `creator`, `updater`, `tenant_id`)
VALUES ('recharge', 1000, 1000, 1, '初始赠送', '1', '1', 1);

-- 智点在线充值：支付应用（回调地址按实际部署地址修改；支付渠道在「支付管理 → 应用信息」里配置微信/支付宝）
DELETE FROM `pay_app` WHERE `app_key` = 'cgch-credit';
INSERT INTO `pay_app` (`app_key`, `name`, `status`, `remark`, `order_notify_url`, `refund_notify_url`, `creator`, `updater`, `tenant_id`)
VALUES ('cgch-credit', '智点充值', 0, 'CGCH 智点在线充值',
        'http://127.0.0.1:48080/admin-api/channel/credit/order/update-paid',
        NULL, '1', '1', 1);
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22303, '在线购买智点', 'channel:credit:buy', 3, 3, 20506, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 数据看板：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/board/overview/index' WHERE `id` = 20001;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22400 AND 22499;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22401, '看板查询', 'channel:dashboard:query', 3, 1, 20001, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- AI 工作报告：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/channel/report/index' WHERE `id` = 20103;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22500 AND 22599;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22501, '工作报告查看与生成', 'channel:ai:report', 3, 1, 20103, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 工作时间：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/config/work-time/index' WHERE `id` = 20502;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22600 AND 22699;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22601, '工作时间设置', 'channel:work-time:config', 3, 1, 20502, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 客户唤醒：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/claw/reactivate/index' WHERE `id` = 20303;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22700 AND 22799;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22701, '客户唤醒', 'channel:reactivate', 3, 1, 20303, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 深度背调：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/claw/bg-check/index' WHERE `id` = 20302;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22800 AND 22899;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22801, '深度背调', 'channel:research', 3, 1, 20302, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 对话助手：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/claw/chat/index' WHERE `id` = 20301;
DELETE FROM `system_menu` WHERE `id` BETWEEN 22900 AND 22999;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (22901, '对话助手', 'channel:assistant', 3, 1, 20301, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 成交案例：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/agent/cases/index' WHERE `id` = 20403;
DELETE FROM `system_menu` WHERE `id` BETWEEN 23000 AND 23099;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (23001, '成交案例', 'channel:ai:case', 3, 1, 20403, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 通知中心：站内信模板（显示在顶部铃铛和「通知中心」页面），templateParams 里另带 link 供跳转
DELETE FROM `system_notify_template` WHERE `code` LIKE 'cgch\_%';
INSERT INTO `system_notify_template` (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `updater`) VALUES
  ('AI 转人工', 'cgch_handoff', 'CGCH 数字员工', '「{customer}」的消息需要人工处理：{reason}', 1, '["customer","reason"]', 0, 'AI 转人工、违禁词拦截、自动发送失败', '1', '1'),
  ('AI 接手回复', 'cgch_takeover', 'CGCH 数字员工', '「{customer}」的消息等待 {minutes} 分钟无人回复，AI 已接手回复', 1, '["customer","minutes"]', 0, '工作时间业务员优先，超时由 AI 接手', '1', '1'),
  ('新询盘建档', 'cgch_new_lead', 'CGCH 数字员工', '新询盘：已为「{name}」自动建线索（来自{source}）', 1, '["name","source"]', 0, '线索自动建档', '1', '1'),
  ('唤醒客户回复', 'cgch_reactivate_reply', 'CGCH 数字员工', '「{customer}」回复了客户唤醒「{task}」，请尽快跟进', 1, '["customer","task"]', 0, '客户唤醒后客户回复', '1', '1'),
  ('智点不足', 'cgch_credit_low', 'CGCH 数字员工', '智点余额仅剩 {balance}，{hint}，请及时充值', 1, '["balance","hint"]', 0, '余额跌破提醒线或用完', '1', '1');

-- 默认租户的联系人设为管理员（智点不足等租户级提醒发给联系人）
UPDATE `system_tenant` SET `contact_user_id` = 1 WHERE `id` = 1 AND `contact_user_id` IS NULL;

-- 通知中心：从占位页切换到正式页面
UPDATE `system_menu` SET `component` = 'cgch/board/notice/index' WHERE `id` = 20003;
