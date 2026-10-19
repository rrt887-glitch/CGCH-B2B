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
