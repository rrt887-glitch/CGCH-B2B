-- 会话工作台：归档、收藏、转交（负责人）、备注名，快捷回复（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `archived` bit(1) NULL DEFAULT b''0'' COMMENT ''是否已归档：归档后不在默认列表里显示，客户再发消息时自动取消归档''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'archived');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `starred` bit(1) NULL DEFAULT b''0'' COMMENT ''是否收藏（星标）''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'starred');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `owner_user_id` bigint NULL COMMENT ''负责人：转交后由该业务员处理；为空表示由号码所属的业务员处理''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'owner_user_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `remark_name` varchar(255) NULL COMMENT ''备注名：业务员给客户起的名字，优先于对方昵称显示''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'remark_name');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
CREATE TABLE IF NOT EXISTS `channel_quick_reply` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope` varchar(255) NULL COMMENT '范围：enterprise 全员可用、personal 仅自己',
  `owner_user_id` bigint NULL COMMENT '个人快捷回复的所属用户',
  `title` varchar(255) NULL COMMENT '标题（输入 / 后按标题搜索）',
  `content` mediumtext NULL COMMENT '内容，可含变量 {name} 客户称呼、{company} 我方公司名',
  `sort` int NULL COMMENT '排序，越小越靠前',
  `use_count` int NULL COMMENT '使用次数，常用的排在前面',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '快捷回复：会话里一键插入的常用话术，分全员（管理员维护）和个人（本人可见）';

DROP PROCEDURE IF EXISTS cgch_add_index;
DELIMITER $$
CREATE PROCEDURE cgch_add_index(IN t VARCHAR(64), IN i VARCHAR(64), IN c1 VARCHAR(64), IN c2 VARCHAR(64), IN c3 VARCHAR(64))
BEGIN
  DECLARE need INT DEFAULT 1 + (c2 IS NOT NULL) + (c3 IS NOT NULL);
  DECLARE have INT;
  SELECT COUNT(*) INTO have FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND COLUMN_NAME IN (c1, IFNULL(c2, c1), IFNULL(c3, c1));
  IF have = need AND NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = t AND INDEX_NAME = i) THEN
    SET @ddl = CONCAT('ALTER TABLE `', t, '` ADD INDEX `', i, '` (`', c1, '`',
                      IF(c2 IS NULL, '', CONCAT(', `', c2, '`')), IF(c3 IS NULL, '', CONCAT(', `', c3, '`')), ')');
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL cgch_add_index('channel_wa_conversation', 'idx_owner', 'owner_user_id', NULL, NULL); -- 转交给我的会话
CALL cgch_add_index('channel_quick_reply', 'idx_owner', 'owner_user_id', NULL, NULL); -- 我的快捷回复
DROP PROCEDURE cgch_add_index;

-- 全员快捷回复维护权限；已有「数字员工配置」权限的角色同时获得
DELETE FROM `system_menu` WHERE `id` = 23203;
INSERT INTO `system_menu` (`id`, `name`, `permission`, `type`, `sort`, `parent_id`, `path`, `icon`, `component`, `component_name`, `status`, `visible`, `keep_alive`, `always_show`, `creator`, `updater`, `deleted`) VALUES
  (23203, '全员快捷回复维护', 'channel:quick-reply:manage', 3, 2, 20503, '', '', '', NULL, 0, b'1', b'1', b'1', 'admin', 'admin', b'0');
INSERT INTO `system_role_menu` (`role_id`, `menu_id`, `creator`, `updater`, `deleted`, `tenant_id`)
SELECT rm.`role_id`, 23203, 'admin', 'admin', b'0', rm.`tenant_id`
  FROM `system_role_menu` rm
 WHERE rm.`menu_id` = 22201 AND rm.`deleted` = b'0'
   AND NOT EXISTS (SELECT 1 FROM `system_role_menu` x WHERE x.`role_id` = rm.`role_id` AND x.`menu_id` = 23203 AND x.`deleted` = b'0');
UPDATE `system_tenant_package` SET `menu_ids` = JSON_ARRAY_APPEND(`menu_ids`, '$', 23203)
 WHERE JSON_VALID(`menu_ids`) AND JSON_CONTAINS(`menu_ids`, '20503') AND NOT JSON_CONTAINS(`menu_ids`, '23203');
