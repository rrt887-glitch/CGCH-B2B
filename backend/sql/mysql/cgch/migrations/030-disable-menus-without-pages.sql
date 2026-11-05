-- 停用没有前端页面的菜单（EAM 资产管理、CMS 内容管理、FMS 财务管理，以及两个示例页），
-- 避免登录后控制台报 route component is invalid、点进去是空白页（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
UPDATE `system_menu` SET `status` = 1
WHERE `id` IN (1637, 12940, 1894, 83, 349)
   OR `component` LIKE 'eam/%' OR `component` LIKE 'cms/%' OR `component` LIKE 'fms/%';
