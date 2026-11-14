-- =====================================================================================================
-- 把 1 号租户（平台）里的业务数据搬到一家新客户（例如 HIG），让 1 号租户只留给平台运营。
-- 这是手动执行的一次性脚本，不在 migrations/ 里，不会被安装脚本自动执行。
--
-- 会搬的：所有带 tenant_id 的 channel_* 和 crm_* 表里属于 1 号租户的数据（会话、消息、渠道账号、AI 设置、知识库、
--        草稿、智点、CRM 客户 / 线索 / 商机 / 合同 / 跟进……），以及指定的员工账号。
-- 不会搬的：平台管理员 admin（编号 1）、系统配置、菜单、字典、文件存储配置、支付配置。
--
-- 执行步骤（务必按顺序）：
--   1. 备份数据库：
--        docker compose -f /opt/cgch/docker-compose.yml exec -T mysql \
--          mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction ruoyi-vue-pro > /root/cgch-before-move.sql
--   2. 平台账号登录 →「客户管理」→「开户」，给 HIG 开户（套餐选「客户标准版」），记下新客户的编号和管理员账号的编号：
--        SELECT id, name, contact_user_id FROM system_tenant ORDER BY id DESC LIMIT 3;
--      开户后不要用新账号登录、不要在新客户里做任何操作，直接执行本脚本。
--   3. 修改下面三个变量后执行：
--        docker compose -f /opt/cgch/docker-compose.yml exec -T mysql \
--          mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 ruoyi-vue-pro < move-tenant1-business-to-new-tenant.sql
--   4. 清缓存并重启后端（所有人需要重新登录）：
--        docker compose -f /opt/cgch/docker-compose.yml exec redis redis-cli FLUSHDB
--        docker compose -f /opt/cgch/docker-compose.yml restart backend
--   5. 用新客户的管理员登录检查会话、客户、渠道是否都在；用 admin 登录确认平台里已经没有业务数据。
--
-- 出错时：脚本在一个事务里执行，任何一步失败都会整体回滚；也可以用第 1 步的备份恢复。
-- =====================================================================================================
SET NAMES utf8mb4;

-- ▼▼▼ 修改这里 ▼▼▼
SET @new_tenant_id   = 0;      -- 第 2 步新开客户的编号（system_tenant.id）
SET @new_admin_id    = 0;      -- 新客户管理员的用户编号（system_tenant.contact_user_id）
SET @move_user_ids   = '249,250';  -- 要一起搬过去的员工账号编号，逗号分隔（249 慧怡，250 metareview；不需要的删掉）
-- ▲▲▲ 修改这里 ▲▲▲

DROP PROCEDURE IF EXISTS cgch_move_tenant1;
DELIMITER $$
CREATE PROCEDURE cgch_move_tenant1()
BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE t VARCHAR(64);
  DECLARE c VARCHAR(64);
  DECLARE n BIGINT;
  DECLARE admin_role BIGINT;
  DECLARE member_role BIGINT;
  DECLARE cur_tables CURSOR FOR
    SELECT TABLE_NAME FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'tenant_id'
       AND (TABLE_NAME LIKE 'channel\_%' OR TABLE_NAME LIKE 'crm\_%');
  DECLARE cur_owner CURSOR FOR
    SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME IN ('user_id', 'owner_user_id')
       AND (TABLE_NAME LIKE 'channel\_%' OR TABLE_NAME LIKE 'crm\_%')
       AND TABLE_NAME IN (SELECT TABLE_NAME FROM information_schema.COLUMNS
                           WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'tenant_id');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    RESIGNAL;
  END;

  -- 0. 检查：新客户存在、不是 1 号、管理员属于新客户、新客户还没有业务数据
  IF @new_tenant_id IS NULL OR @new_tenant_id <= 1
     OR NOT EXISTS (SELECT 1 FROM system_tenant WHERE id = @new_tenant_id AND deleted = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '请先把 @new_tenant_id 改成新开客户的编号';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM system_users WHERE id = @new_admin_id AND tenant_id = @new_tenant_id AND deleted = b'0') THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '@new_admin_id 不是新客户的管理员账号';
  END IF;
  IF FIND_IN_SET('1', @move_user_ids) > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '平台管理员 admin（编号 1）不能搬走';
  END IF;
  IF EXISTS (SELECT 1 FROM channel_wa_conversation WHERE tenant_id = @new_tenant_id)
     OR EXISTS (SELECT 1 FROM channel_wa_account WHERE tenant_id = @new_tenant_id)
     OR EXISTS (SELECT 1 FROM crm_customer WHERE tenant_id = @new_tenant_id) THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新客户里已经有业务数据，请换一家刚开户、没有使用过的客户';
  END IF;

  SELECT id INTO admin_role FROM system_role WHERE tenant_id = @new_tenant_id AND code = 'tenant_admin' AND deleted = b'0' LIMIT 1;
  SELECT id INTO member_role FROM system_role WHERE tenant_id = @new_tenant_id AND code = 'cgch_sales' AND deleted = b'0' LIMIT 1;
  IF admin_role IS NULL OR member_role IS NULL THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '新客户缺少管理员或成员角色，请用新版本的「开户」重新开户';
  END IF;

  START TRANSACTION;

  -- 1. 开户时自动准备的 CRM 初始数据（商机阶段、公海规则）删掉，改用 1 号租户原来那套（已有商机引用它们）
  DELETE FROM crm_business_status WHERE tenant_id = @new_tenant_id;
  DELETE FROM crm_business_status_type WHERE tenant_id = @new_tenant_id;
  DELETE FROM crm_customer_pool_config WHERE tenant_id = @new_tenant_id;
  DELETE FROM channel_credit_ledger WHERE tenant_id = @new_tenant_id;
  DELETE FROM channel_credit_account WHERE tenant_id = @new_tenant_id;
  DELETE FROM channel_ai_config WHERE tenant_id = @new_tenant_id;

  -- 2. 业务表：tenant_id 1 → 新客户
  SET done = 0;
  OPEN cur_tables;
  move_loop: LOOP
    FETCH cur_tables INTO t;
    IF done = 1 THEN LEAVE move_loop; END IF;
    SET @sql = CONCAT('UPDATE `', t, '` SET `tenant_id` = ', @new_tenant_id, ' WHERE `tenant_id` = 1');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur_tables;

  -- 3. 原来属于 admin（编号 1）的号码、客户、线索等，改由新客户的管理员负责
  SET done = 0;
  OPEN cur_owner;
  owner_loop: LOOP
    FETCH cur_owner INTO t, c;
    IF done = 1 THEN LEAVE owner_loop; END IF;
    SET @sql = CONCAT('UPDATE `', t, '` SET `', c, '` = ', @new_admin_id,
                      ' WHERE `tenant_id` = ', @new_tenant_id, ' AND `', c, '` = 1');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur_owner;

  -- 4. 员工账号：搬到新客户，原来是超级管理员的成为新客户的管理员，其他人成为成员；清掉部门、岗位和登录令牌
  UPDATE system_users SET tenant_id = @new_tenant_id, dept_id = NULL, post_ids = '[]'
   WHERE tenant_id = 1 AND FIND_IN_SET(id, @move_user_ids) > 0;
  DELETE FROM system_user_post WHERE FIND_IN_SET(user_id, @move_user_ids) > 0;
  UPDATE system_user_role ur
     SET ur.role_id = IF(ur.role_id IN (SELECT r.id FROM system_role r WHERE r.code IN ('super_admin', 'tenant_admin')), admin_role, member_role),
         ur.tenant_id = @new_tenant_id
   WHERE FIND_IN_SET(ur.user_id, @move_user_ids) > 0 AND ur.deleted = b'0';
  INSERT INTO system_user_role (user_id, role_id, creator, updater, deleted, tenant_id)
  SELECT u.id, member_role, '1', '1', b'0', @new_tenant_id FROM system_users u
   WHERE FIND_IN_SET(u.id, @move_user_ids) > 0
     AND NOT EXISTS (SELECT 1 FROM system_user_role x WHERE x.user_id = u.id AND x.deleted = b'0');
  DELETE FROM system_oauth2_access_token WHERE user_type = 2 AND FIND_IN_SET(user_id, @move_user_ids) > 0;
  DELETE FROM system_oauth2_refresh_token WHERE user_type = 2 AND FIND_IN_SET(user_id, @move_user_ids) > 0;

  -- 5. 账号数上限不够时自动放宽（至少容纳管理员 + 搬过去的员工）
  SELECT COUNT(*) INTO n FROM system_users WHERE tenant_id = @new_tenant_id AND deleted = b'0';
  UPDATE system_tenant SET account_count = GREATEST(account_count, n) WHERE id = @new_tenant_id;

  COMMIT;

  -- 结果
  SELECT '已搬到新客户' AS `结果`, @new_tenant_id AS `新客户编号`,
         (SELECT COUNT(*) FROM channel_wa_conversation WHERE tenant_id = @new_tenant_id) AS `会话`,
         (SELECT COUNT(*) FROM channel_wa_account WHERE tenant_id = @new_tenant_id) AS `渠道账号`,
         (SELECT COUNT(*) FROM crm_customer WHERE tenant_id = @new_tenant_id) AS `客户`,
         (SELECT COUNT(*) FROM system_users WHERE tenant_id = @new_tenant_id AND deleted = b'0') AS `账号`,
         (SELECT COUNT(*) FROM channel_wa_conversation WHERE tenant_id = 1) AS `平台剩余会话`;
END$$
DELIMITER ;

CALL cgch_move_tenant1();
DROP PROCEDURE IF EXISTS cgch_move_tenant1;
