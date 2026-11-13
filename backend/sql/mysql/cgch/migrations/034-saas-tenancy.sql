-- 多家客户（多租户 SaaS）：账号全平台唯一（邮箱 / 手机号，最长 64 位）、会话「公开给团队」、平台「进入客户后台」记录、
-- 客户标准版套餐（开户默认选它）。已有数据库执行一次，可重复执行；新装数据库由 00-channel-schema.sql / 03-channel-seed.sql 建好。
SET NAMES utf8mb4;

-- 1. 登录账号放宽到 64 位（邮箱可能较长）
SET @ddl = (SELECT IF(CHARACTER_MAXIMUM_LENGTH < 64,
  'ALTER TABLE `system_users` MODIFY COLUMN `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT ''用户账号（邮箱或手机号，全平台唯一）''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'system_users' AND COLUMN_NAME = 'username');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 会话「公开给团队」
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_wa_conversation` ADD COLUMN `team_public` bit(1) NULL DEFAULT b''0'' COMMENT ''公开给团队：开启后本公司全部成员都能在「团队」里查看（只能看、写内部备注）'' AFTER `ai_enabled`',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_wa_conversation' AND COLUMN_NAME = 'team_public');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 平台「进入客户后台」记录（平台级数据，不属于任何一家客户）
CREATE TABLE IF NOT EXISTS `system_tenant_visit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '进入的平台账号',
  `username` varchar(64) NULL COMMENT '平台账号',
  `nickname` varchar(64) NULL COMMENT '平台账号姓名',
  `visit_tenant_id` bigint NOT NULL COMMENT '进入的客户（租户）编号',
  `visit_tenant_name` varchar(64) NULL COMMENT '客户名称',
  `reason` varchar(255) NOT NULL COMMENT '进入原因',
  `ip` varchar(64) NULL COMMENT '来源 IP',
  `user_agent` varchar(512) NULL COMMENT '浏览器',
  `enter_time` datetime NOT NULL COMMENT '进入时间',
  `expire_time` datetime NOT NULL COMMENT '有效期至（到期需重新进入）',
  `exit_time` datetime NULL COMMENT '主动退出时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  PRIMARY KEY (`id`),
  KEY `idx_visit_tenant` (`visit_tenant_id`, `id`),
  KEY `idx_user_tenant` (`user_id`, `visit_tenant_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '平台进入客户后台的记录';

-- 4. 客户标准版套餐（编号 200）：草根出海全部功能 + 成员管理（添加成员、重置密码、分配角色），
--    不含平台功能（租户、套餐、系统配置等）和智点充值。开户时的「管理员」拥有套餐全部权限，「成员」以平台的业务员角色为模板。
--    只在没有时创建；平台运营之后在套餐里调整的不会被覆盖。
INSERT INTO `system_tenant_package` (`id`, `name`, `status`, `remark`, `menu_ids`, `creator`, `updater`, `deleted`)
SELECT 200, '客户标准版', 0, '草根出海全部功能 + 成员管理；不含平台功能和智点充值', CAST(JSON_ARRAYAGG(m.`id`) AS CHAR), '1', '1', b'0'
  FROM `system_menu` m
  LEFT JOIN (
    WITH RECURSIVE sub AS (
      SELECT `id` FROM `system_menu` WHERE (`id` = 480 OR `id` BETWEEN 20000 AND 29999) AND `deleted` = b'0'
      UNION
      SELECT c.`id` FROM `system_menu` c JOIN sub ON c.`parent_id` = sub.`id` WHERE c.`deleted` = b'0'
    )
    SELECT `id` FROM sub
  ) allowed ON allowed.`id` = m.`id`
 WHERE m.`deleted` = b'0' AND m.`status` = 0
   AND (allowed.`id` IS NOT NULL
        -- 系统管理 › 用户管理（查询、新增、修改、删除、重置密码）、角色管理 › 设置用户角色
        OR m.`id` IN (1, 4, 23, 24, 25, 26, 29, 5, 80))
   AND COALESCE(m.`permission`, '') <> 'channel:credit:recharge'
   AND NOT EXISTS (SELECT 1 FROM `system_tenant_package` p WHERE p.`id` = 200)
HAVING COUNT(*) > 0;
