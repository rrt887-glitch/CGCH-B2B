-- 049：成员可维护「标签」和「客户分组」
--
-- 背景：CRM 设置里这四项原本只有租户管理员能改：
--   标签管理 / 客户分组 / 客户公海规则 / 线索流转规则
-- 按需求（方案 B）只放开前两项的新建与修改，公海规则和线索流转仍然只有管理员能动。
--
-- 影响范围：所有租户里 code = 'cgch_sales' 的「成员」角色（含以后新建的租户，通过套餐菜单）。
-- 可重复执行。

SET NAMES utf8mb4;

-- ---------- 1. 已有租户：给成员角色补菜单授权 ----------
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT r.`id`, m.`id`, 'admin', 'admin', b'0', r.`tenant_id`
FROM `system_role` r
JOIN `system_menu` m
  ON m.`permission` IN ('crm:tag:create', 'crm:tag:update',
                        'crm:customer-group:create', 'crm:customer-group:update')
WHERE r.`code` = 'cgch_sales' AND r.`deleted` = b'0' AND m.`deleted` = b'0'
  AND NOT EXISTS (
    SELECT 1 FROM `system_role_menu` x
    WHERE x.`role_id` = r.`id` AND x.`menu_id` = m.`id` AND x.`deleted` = b'0'
  );

-- ---------- 2. 套餐菜单：以后新建租户的成员也自带这两组权限 ----------
UPDATE `system_tenant_package` p
JOIN `system_menu` m
  ON m.`permission` IN ('crm:tag:create', 'crm:tag:update',
                        'crm:customer-group:create', 'crm:customer-group:update')
SET p.`menu_ids` = JSON_ARRAY_APPEND(p.`menu_ids`, '$', m.`id`)
WHERE m.`deleted` = b'0'
  AND JSON_VALID(p.`menu_ids`)
  AND NOT JSON_CONTAINS(p.`menu_ids`, CAST(m.`id` AS JSON));
