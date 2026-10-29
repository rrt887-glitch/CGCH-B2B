-- 企业云盘、全员邮件模板维护权限（与 03-channel-seed.sql 末尾相同，可重复执行）
SET NAMES utf8mb4;
-- 企业云盘、全员邮件模板的维护权限：企业云盘的文件 AI 会发给客户，全员模板所有业务员都在用，只有管理员等有权限的人能修改
DELETE FROM `system_menu` WHERE `id` BETWEEN 23200 AND 23299;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (23201, '企业云盘维护', 'channel:cloud:manage', 3, 1, 20504, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0'),
  (23202, '全员邮件模板维护', 'channel:mail-template:manage', 3, 1, 20503, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
-- 已有「数字员工配置」权限（管理员类角色）的角色同时获得这两项，保持原来能维护的人仍能维护
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, m.`id`, 'admin', 'admin', b'0', rm.`tenant_id`
  FROM `system_role_menu` rm JOIN `system_menu` m ON m.`id` IN (23201, 23202)
 WHERE rm.`menu_id` = 22201 AND rm.`deleted` = b'0'
   AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0');
-- 已包含云盘 / 模板页面的租户套餐，补上这两项权限
UPDATE `system_tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 23201)
 WHERE JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '20504') AND NOT JSON_CONTAINS(`menu_ids`, '23201');
UPDATE `system_tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 23202)
 WHERE JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '20503') AND NOT JSON_CONTAINS(`menu_ids`, '23202');
