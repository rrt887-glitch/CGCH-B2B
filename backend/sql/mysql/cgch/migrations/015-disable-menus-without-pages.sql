-- 上游菜单里有财务（fms）和内容管理（cms）模块，但本项目没有这些页面，登录后浏览器控制台会报 route component is invalid。
-- 停用这两个模块的全部菜单（含子菜单和按钮权限），可重复执行
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
