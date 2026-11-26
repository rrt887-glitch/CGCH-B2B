-- 048：清理演示模块菜单与失效的外链头像
--
-- 现象（浏览器控制台）：
--   menu name duplicate: 库存管理 / 基础数据 / 流程管理 / 考勤管理
--   Failed to load resource: 404  s1.ax1x.com/...（头像）
--
-- 原因：
--   1) 早期把 yudao 的演示模块也导进来了：「WMS 系统」(1348)、「OA 办公协同」(8200)
--      下面挂着「库存管理/基础数据/流程管理/考勤管理」，和真实模块里的同名菜单重复，
--      前端生成路由时会打印 menu name duplicate。
--   2) 部分演示账号/角色头像指向 ax1x.com，图床已挂，前端一直 404。
--
-- 做法：把这两个演示模块整棵子树软删除（连同角色授权），并清空外链头像。
-- 注意：MySQL 不允许同一条语句里「插入并查询同一个临时表」(ERROR 1137)，
--       所以按层级用不同临时表收集子孙。
-- 可重复执行。

SET NAMES utf8mb4;

DROP TEMPORARY TABLE IF EXISTS `tmp_menu_l1`, `tmp_menu_l2`, `tmp_menu_l3`,
  `tmp_menu_l4`, `tmp_menu_l5`, `tmp_menu_l6`, `tmp_menu_all`;

CREATE TEMPORARY TABLE `tmp_menu_l1` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_l1` (`id`) VALUES (1348), (8200);

CREATE TEMPORARY TABLE `tmp_menu_l2` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_l2` (`id`)
SELECT `id` FROM `system_menu` WHERE `parent_id` IN (SELECT `id` FROM `tmp_menu_l1`);

CREATE TEMPORARY TABLE `tmp_menu_l3` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_l3` (`id`)
SELECT `id` FROM `system_menu` WHERE `parent_id` IN (SELECT `id` FROM `tmp_menu_l2`);

CREATE TEMPORARY TABLE `tmp_menu_l4` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_l4` (`id`)
SELECT `id` FROM `system_menu` WHERE `parent_id` IN (SELECT `id` FROM `tmp_menu_l3`);

CREATE TEMPORARY TABLE `tmp_menu_l5` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_l5` (`id`)
SELECT `id` FROM `system_menu` WHERE `parent_id` IN (SELECT `id` FROM `tmp_menu_l4`);

CREATE TEMPORARY TABLE `tmp_menu_l6` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_l6` (`id`)
SELECT `id` FROM `system_menu` WHERE `parent_id` IN (SELECT `id` FROM `tmp_menu_l5`);

CREATE TEMPORARY TABLE `tmp_menu_all` (`id` bigint NOT NULL PRIMARY KEY) ENGINE = InnoDB;
INSERT IGNORE INTO `tmp_menu_all` (`id`) SELECT `id` FROM `tmp_menu_l1`;
INSERT IGNORE INTO `tmp_menu_all` (`id`) SELECT `id` FROM `tmp_menu_l2`;
INSERT IGNORE INTO `tmp_menu_all` (`id`) SELECT `id` FROM `tmp_menu_l3`;
INSERT IGNORE INTO `tmp_menu_all` (`id`) SELECT `id` FROM `tmp_menu_l4`;
INSERT IGNORE INTO `tmp_menu_all` (`id`) SELECT `id` FROM `tmp_menu_l5`;
INSERT IGNORE INTO `tmp_menu_all` (`id`) SELECT `id` FROM `tmp_menu_l6`;

-- 先撤销角色上的授权，再把菜单软删除（保留 id，便于以后恢复）
DELETE sr FROM `system_role_menu` sr
JOIN `tmp_menu_all` d ON d.`id` = sr.`menu_id`;

UPDATE `system_menu` m
JOIN `tmp_menu_all` d ON d.`id` = m.`id`
SET m.`deleted` = b'1'
WHERE m.`deleted` = b'0';

DROP TEMPORARY TABLE IF EXISTS `tmp_menu_l1`, `tmp_menu_l2`, `tmp_menu_l3`,
  `tmp_menu_l4`, `tmp_menu_l5`, `tmp_menu_l6`, `tmp_menu_all`;

-- ---------- 2. 失效的外链头像 ----------
UPDATE `system_users` SET `avatar` = '' WHERE `avatar` LIKE '%ax1x.com%';
UPDATE `channel_wa_conversation` SET `avatar_url` = '' WHERE `avatar_url` LIKE '%ax1x.com%';

SET @ddl = (SELECT IF(COUNT(*) > 0,
    'UPDATE `channel_ai_role` SET `avatar` = '''' WHERE `avatar` LIKE ''%ax1x.com%''',
    'SELECT 1')
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_role');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
