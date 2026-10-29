-- CGCH 菜单骨架：6 个一级模块 + 占位页面
-- 在 ruoyi-vue-pro.sql 之后执行；可重复执行（先删除本脚本的 id 段再插入）
SET NAMES utf8mb4;

DELETE FROM `system_menu` WHERE `id` BETWEEN 20000 AND 20999;

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20000, '智能看板', '', 1, 1, 0, '/board', 'lucide:layout-dashboard', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20001, '数据看板', '', 2, 1, 20000, 'overview', 'lucide:chart-column', 'cgch/placeholder/index', 'CgchBoardOverview', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20002, '日程安排', '', 2, 2, 20000, 'schedule', 'lucide:calendar-days', 'cgch/placeholder/index', 'CgchBoardSchedule', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20003, '通知中心', '', 2, 3, 20000, 'notice', 'lucide:bell', 'cgch/placeholder/index', 'CgchBoardNotice', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20004, '客户概览', '', 2, 4, 20000, 'customer-overview', 'lucide:users', 'cgch/placeholder/index', 'CgchBoardCustomerOverview', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20100, '渠道消息', '', 1, 2, 0, '/channel-msg', 'lucide:messages-square', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20101, '消息工作台', '', 2, 1, 20100, 'im', 'lucide:message-circle', 'cgch/placeholder/index', 'CgchChannelIm', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20102, '邮件工作台', '', 2, 2, 20100, 'mail', 'lucide:mail', 'cgch/placeholder/index', 'CgchChannelMail', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20103, 'AI 工作报告', '', 2, 3, 20100, 'report', 'lucide:file-chart-column', 'cgch/placeholder/index', 'CgchChannelReport', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20104, '邮件与 IM 模拟', '', 2, 4, 20100, 'simulate', 'lucide:flask-conical', 'cgch/placeholder/index', 'CgchChannelSimulate', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20300, 'CRM Claw', '', 1, 4, 0, '/claw', 'lucide:bot', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20301, '对话助手', '', 2, 1, 20300, 'chat', 'lucide:message-square-text', 'cgch/placeholder/index', 'CgchClawChat', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20302, '深度背调', '', 2, 2, 20300, 'bg-check', 'lucide:search-check', 'cgch/placeholder/index', 'CgchClawBgCheck', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20303, '客户唤醒', '', 2, 3, 20300, 'reactivate', 'lucide:alarm-clock', 'cgch/placeholder/index', 'CgchClawReactivate', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20400, '数字员工管理', '', 1, 5, 0, '/agent', 'lucide:user-cog', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20401, '数字员工', '', 2, 1, 20400, 'profile', 'lucide:bot-message-square', 'cgch/placeholder/index', 'CgchAgentProfile', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20402, '知识库', '', 2, 2, 20400, 'knowledge', 'lucide:book-open', 'cgch/placeholder/index', 'CgchAgentKnowledge', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20403, '成交案例', '', 2, 3, 20400, 'cases', 'lucide:trophy', 'cgch/placeholder/index', 'CgchAgentCases', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20404, 'Skill 配置中心', '', 2, 4, 20400, 'skills', 'lucide:puzzle', 'cgch/placeholder/index', 'CgchAgentSkills', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20405, '违禁词配置', '', 2, 5, 20400, 'forbidden', 'lucide:shield-ban', 'cgch/placeholder/index', 'CgchAgentForbidden', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20500, '配置中台', '', 1, 6, 0, '/config', 'lucide:settings-2', NULL, NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20501, '渠道管理', '', 2, 1, 20500, 'channels', 'lucide:plug', 'cgch/placeholder/index', 'CgchConfigChannels', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20502, '工作时间', '', 2, 2, 20500, 'work-time', 'lucide:clock', 'cgch/placeholder/index', 'CgchConfigWorkTime', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20503, '模板管理', '', 2, 3, 20500, 'templates', 'lucide:layout-template', 'cgch/placeholder/index', 'CgchConfigTemplates', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20504, '云盘管理', '', 2, 4, 20500, 'disk', 'lucide:hard-drive', 'cgch/placeholder/index', 'CgchConfigDisk', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20505, '发送策略', '', 2, 5, 20500, 'send-policy', 'lucide:filter', 'cgch/placeholder/index', 'CgchConfigSendPolicy', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES (20506, '智点消耗', '', 2, 6, 20500, 'credits', 'lucide:coins', 'cgch/placeholder/index', 'CgchConfigCredits', 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 上游 CRM 模块作为「AI-CRM」，排在第 3 位
UPDATE `system_menu` SET `name` = 'AI-CRM', `sort` = 3 WHERE `id` = 480;

-- 平台管理类菜单排到最后
UPDATE `system_menu` SET `sort` = 90 WHERE `id` = 791;  -- AI 大模型
UPDATE `system_menu` SET `sort` = 91 WHERE `id` = 114;  -- 支付管理
UPDATE `system_menu` SET `sort` = 98 WHERE `id` = 1;    -- 系统管理
UPDATE `system_menu` SET `sort` = 99 WHERE `id` = 2;    -- 基础设施

-- 隐藏后端已移除模块的菜单与上游文档链接
UPDATE `system_menu` SET `visible` = b'0'
WHERE `id` IN (148, 194, 207, 272, 347, 348, 373, 449, 597, 860, 959, 1348, 1418, 1476, 1637, 1894, 8000, 8200, 12940);

-- 财务（fms）、内容管理（cms）模块本项目没有页面，停用其菜单（与 migrations/015 相同）
SET NAMES utf8mb4;
UPDATE `system_menu` m
  JOIN (
    WITH RECURSIVE sub AS (
      SELECT `id` FROM `system_menu` WHERE `parent_id` = 0 AND `path` IN ('/fms', '/cms', 'fms', 'cms') AND `deleted` = b'0'
      UNION ALL
      SELECT c.`id` FROM `system_menu` c JOIN sub ON c.`parent_id` = sub.`id` WHERE c.`deleted` = b'0'
    )
    SELECT `id` FROM sub
  ) t ON t.`id` = m.`id`
   SET m.`status` = 1;
-- 其他位置引用了这两个模块页面的菜单
UPDATE `system_menu` SET `status` = 1
 WHERE `deleted` = b'0' AND (`component` LIKE 'fms/%' OR `component` LIKE 'cms/%');
