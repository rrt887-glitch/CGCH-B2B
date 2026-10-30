-- CGCH：AI-CRM 初始数据（外贸商机阶段、CRM 设置与查重菜单）
-- 在 00-crm-schema.sql 与 01-menu.sql 之后执行；可重复执行
SET NAMES utf8mb4;

-- creator/updater 填用户编号（CRM 接口会按用户编号解析）
-- 外贸标准商机流程：8 个阶段，适用于全部部门（dept_ids 为 NULL）
DELETE FROM `crm_business_status` WHERE `type_id` = 1 AND `tenant_id` = 1;
DELETE FROM `crm_business_status_type` WHERE `id` = 1 AND `tenant_id` = 1;
INSERT INTO `crm_business_status_type` (`id`, `name`, `dept_ids`, `creator`, `updater`, `tenant_id`)
VALUES (1, '外贸标准流程', NULL, '1', '1', 1);
INSERT INTO `crm_business_status` (`type_id`, `name`, `percent`, `sort`, `creator`, `updater`, `tenant_id`) VALUES
  (1, '初步回复', 5,  1, '1', '1', 1),
  (1, '背调',     10, 2, '1', '1', 1),
  (1, '需求挖掘', 20, 3, '1', '1', 1),
  (1, '报价',     35, 4, '1', '1', 1),
  (1, '样品',     50, 5, '1', '1', 1),
  (1, '合同签订', 70, 6, '1', '1', 1),
  (1, '订单执行', 90, 7, '1', '1', 1),
  (1, '赢单',     100, 8, '1', '1', 1);

-- AI-CRM 下新增「CRM 设置」「查重」两个页面及按钮权限
DELETE FROM `system_menu` WHERE `id` BETWEEN 21000 AND 21099;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (21000, 'CRM 设置', '', 2, 90, 480, 'cgch-settings', 'lucide:sliders-horizontal', 'cgch/crm/settings/index', 'CgchCrmSettings', 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21001, '标签查询', 'crm:tag:query', 3, 1, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21002, '标签新增', 'crm:tag:create', 3, 2, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21003, '标签修改', 'crm:tag:update', 3, 3, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21004, '标签删除', 'crm:tag:delete', 3, 4, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21005, '分组查询', 'crm:customer-group:query', 3, 5, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21006, '分组新增', 'crm:customer-group:create', 3, 6, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21007, '分组修改', 'crm:customer-group:update', 3, 7, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21008, '分组删除', 'crm:customer-group:delete', 3, 8, 21000, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21010, '查重', '', 2, 91, 480, 'cgch-dedup', 'lucide:copy-check', 'cgch/crm/dedup/index', 'CgchCrmDedup', 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (21011, '查重搜索', 'crm:dedup:query', 3, 1, 21010, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');

-- 默认公海规则：30 天未跟进或 90 天未成交自动回收，提前 3 天提醒负责人
DELETE FROM `crm_customer_pool_config` WHERE `tenant_id` = 1;
INSERT INTO `crm_customer_pool_config` (`enabled`, `contact_expire_days`, `deal_expire_days`, `notify_enabled`, `notify_days`, `creator`, `updater`, `tenant_id`)
VALUES (b'1', 30, 90, b'1', 3, '1', '1', 1);

-- 客户来源补充「WhatsApp」（线索自动建档使用；邮件询盘使用已有的「邮件咨询」= 10）
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` = '11';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
VALUES (11, 'WhatsApp', '11', 'crm_customer_source', 0, 'success', '', 'CGCH 线索自动建档', '1', '1', b'0');

-- 客户来源补充：留资邮箱识别的平台询盘
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` IN ('12', '13', '14');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`) VALUES
  (12, '阿里国际站', '12', 'crm_customer_source', 0, 'warning', '', 'CGCH 留资邮箱', '1', '1', b'0'),
  (13, '中国制造网', '13', 'crm_customer_source', 0, 'danger', '', 'CGCH 留资邮箱', '1', '1', b'0'),
  (14, '官网表单', '14', 'crm_customer_source', 0, 'primary', '', 'CGCH 留资邮箱', '1', '1', b'0');

-- 客户来源补充：社媒私信
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` IN ('15', '16');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`) VALUES
  (15, 'Facebook', '15', 'crm_customer_source', 0, 'primary', '', 'CGCH 社媒私信', '1', '1', b'0'),
  (16, 'Instagram', '16', 'crm_customer_source', 0, 'danger', '', 'CGCH 社媒私信', '1', '1', b'0');

-- 客户来源补充：官网 IM 在线聊天
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` = '17';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`) VALUES
  (17, '官网IM', '17', 'crm_customer_source', 0, 'primary', '', 'CGCH 官网在线聊天', '1', '1', b'0');

-- 客户来源补充：视频评论
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` IN ('18', '19');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`) VALUES
  (18, 'YouTube', '18', 'crm_customer_source', 0, 'danger', '', 'CGCH 视频评论', '1', '1', b'0'),
  (19, 'TikTok', '19', 'crm_customer_source', 0, 'default', '', 'CGCH 视频评论', '1', '1', b'0');

-- 客户级别改为原网站的 A/B/C/D 四类（AI 按「配技能 → CRM 自动录入 → 客户评级」规则打级）
UPDATE `system_dict_data` SET `label` = 'A类客户', `remark` = '成熟的品牌客户' WHERE `dict_type` = 'crm_customer_level' AND `value` = '1';
UPDATE `system_dict_data` SET `label` = 'B类客户', `remark` = '初创的品牌客户' WHERE `dict_type` = 'crm_customer_level' AND `value` = '2';
UPDATE `system_dict_data` SET `label` = 'C类客户', `remark` = '创业者' WHERE `dict_type` = 'crm_customer_level' AND `value` = '3';
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_level' AND `value` = '4';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
VALUES (4, 'D类客户', '4', 'crm_customer_level', 0, 'default', '', '自用', '1', '1', b'0');

-- 客户阶段（新询盘 → 报价 → 样品 → 谈判 → 成交 → 复购）
INSERT INTO `system_dict_type` (`name`, `type`, `status`, `remark`, `creator`, `updater`, `deleted`)
SELECT '客户阶段', 'crm_customer_stage', 0, '客户从询盘到复购的阶段，可在这里增改', '1', '1', b'0' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_type` WHERE `type` = 'crm_customer_stage' AND `deleted` = b'0');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
SELECT s.sort, s.label, s.value, 'crm_customer_stage', 0, s.color, '', '', '1', '1', b'0' FROM (
  SELECT 1 AS sort, '新询盘' AS label, '1' AS value, 'processing' AS color UNION ALL
  SELECT 2, '报价', '2', 'warning' UNION ALL
  SELECT 3, '样品', '3', 'warning' UNION ALL
  SELECT 4, '谈判', '4', 'danger' UNION ALL
  SELECT 5, '成交', '5', 'success' UNION ALL
  SELECT 6, '复购', '6', 'success') s
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d WHERE d.`dict_type` = 'crm_customer_stage' AND d.`value` = s.value AND d.`deleted` = b'0');

-- 跟进方式加上外贸常用的 WhatsApp、邮件、视频会议
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
SELECT s.sort, s.label, s.value, 'crm_follow_up_type', 0, '', '', '', '1', '1', b'0' FROM (
  SELECT 5 AS sort, 'WhatsApp' AS label, '5' AS value UNION ALL
  SELECT 6, '邮件', '6' UNION ALL
  SELECT 7, '视频会议', '7') s
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d WHERE d.`dict_type` = 'crm_follow_up_type' AND d.`value` = s.value AND d.`deleted` = b'0');
