-- 物料库：知识库条目分公司物料和个人物料，并按产品、资质、价格政策等分类（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_ai_knowledge` ADD COLUMN `scope` varchar(16) NULL COMMENT ''范围：enterprise 公司物料（为空同此）、personal 个人物料（只在所属业务员负责的会话里使用）''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_knowledge' AND COLUMN_NAME = 'scope');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_ai_knowledge` ADD COLUMN `owner_user_id` bigint NULL COMMENT ''个人物料的所属用户''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_knowledge' AND COLUMN_NAME = 'owner_user_id');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @ddl = (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE `channel_ai_knowledge` ADD COLUMN `category` varchar(32) NULL COMMENT ''分类：product 产品、certificate 资质认证、price 价格政策、logistics 物流交期、aftersale 售后服务、other 其他''',
  'SELECT 1')
  FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'channel_ai_knowledge' AND COLUMN_NAME = 'category');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
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
CALL cgch_add_index('channel_ai_knowledge', 'idx_owner', 'owner_user_id', NULL, NULL);
DROP PROCEDURE cgch_add_index;
