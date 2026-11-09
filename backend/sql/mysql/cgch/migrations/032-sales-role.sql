-- 两种账号：超级管理员（全部权限、看全部数据）+ 业务员（日常工作功能、只看自己的数据）。
-- 业务员角色：CRM 与草根出海各功能的菜单和按钮，不含系统管理、平台配置和公司级设置（大模型、知识库、产品、标签、
-- 公海规则、导入导出、分配客户、全员模板、云盘维护等），数据范围「仅本人」。删除只能删自己负责的记录（CRM 自带负责人校验）。可重复执行。
SET NAMES utf8mb4;
INSERT INTO `system_role` (`id`, `name`, `code`, `sort`, `data_scope`, `data_scope_dept_ids`, `status`, `type`, `remark`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 300, '业务员', 'cgch_sales', 3, 5, '', 0, 1, '日常接待、跟进客户；只看自己负责的数据', 'admin', 'admin', b'0', 1
 WHERE NOT EXISTS (SELECT 1 FROM `system_role` WHERE `id` = 300);

INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT 300, m.`id`, 'admin', 'admin', b'0', 1
  FROM `system_menu` m
  JOIN (
    WITH RECURSIVE sub AS (
      SELECT `id` FROM `system_menu` WHERE (`id` = 480 OR `id` BETWEEN 20000 AND 29999) AND `deleted` = b'0'
      UNION
      SELECT c.`id` FROM `system_menu` c JOIN sub ON c.`parent_id` = sub.`id` WHERE c.`deleted` = b'0'
    )
    SELECT `id` FROM sub
  ) allowed ON allowed.`id` = m.`id`
 WHERE m.`deleted` = b'0' AND m.`status` = 0
   AND COALESCE(m.`permission`, '') NOT IN (
     'channel:ai:config', 'channel:ai:forbidden', 'channel:ai:knowledge', 'channel:ai:case',
     'channel:cloud:manage', 'channel:mail-template:manage', 'channel:work-time:config',
     'channel:credit:buy', 'channel:credit:recharge',
     'crm:customer:distribute', 'crm:customer:import', 'crm:customer:export', 'crm:clue:export', 'crm:contact:export',
     'crm:business:export', 'crm:contract:export', 'crm:receivable:export', 'crm:receivable-plan:export', 'crm:product:export',
     'crm:business-status:create', 'crm:business-status:update', 'crm:business-status:delete',
     'crm:contract-config:update', 'crm:customer-pool-config:update',
     'crm:customer-limit-config:create', 'crm:customer-limit-config:update', 'crm:customer-limit-config:delete', 'crm:customer-limit-config:export',
     'crm:performance-config:create', 'crm:performance-config:update', 'crm:performance-config:delete',
     'crm:product:create', 'crm:product:update', 'crm:product:delete',
     'crm:product-category:create', 'crm:product-category:update', 'crm:product-category:delete',
     'crm:customer-group:create', 'crm:customer-group:update', 'crm:customer-group:delete',
     'crm:tag:create', 'crm:tag:update', 'crm:tag:delete')
   AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = 300 AND x.`menu_id` = m.`id` AND x.`deleted` = b'0');
