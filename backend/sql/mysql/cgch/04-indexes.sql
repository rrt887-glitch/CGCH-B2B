-- 常用查询的索引（可重复执行：已有的索引、不存在的表或字段会跳过）
-- 新装数据库在建表后执行；已有数据库由 migrations/016-indexes.sql（内容相同）执行一次。
-- InnoDB 在线加索引，不锁表，数据多时需要一些时间
SET NAMES utf8mb4;
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
CALL cgch_add_index('channel_wa_message', 'idx_account_wamid', 'account_id', 'wa_message_id', NULL); -- 按消息编号查重（同步聊天记录、状态回执）
CALL cgch_add_index('channel_wa_message', 'idx_conversation_id', 'conversation_id', 'id', NULL); -- 打开会话时按会话取最近消息
CALL cgch_add_index('channel_wa_message', 'idx_conversation_time', 'conversation_id', 'message_time', NULL); -- 已读回执、会话内统计
CALL cgch_add_index('channel_wa_message', 'idx_message_time', 'message_time', NULL, NULL); -- 看板、报告按时间统计
CALL cgch_add_index('channel_wa_conversation', 'idx_account_waid', 'account_id', 'wa_id', NULL); -- 收到消息时找到客户所在会话
CALL cgch_add_index('channel_wa_conversation', 'idx_account_last', 'account_id', 'last_message_time', NULL); -- 会话列表按最近消息排序
CALL cgch_add_index('channel_wa_conversation', 'idx_customer', 'customer_id', NULL, NULL); -- 客户详情里的会话
CALL cgch_add_index('channel_wa_conversation', 'idx_clue', 'clue_id', NULL, NULL); -- 线索详情里的会话
CALL cgch_add_index('channel_wa_account', 'idx_phone_number_id', 'phone_number_id', NULL, NULL); -- 平台推送时找到账号
CALL cgch_add_index('channel_wa_account', 'idx_user', 'user_id', NULL, NULL); -- 业务员自己的账号
CALL cgch_add_index('channel_wa_account', 'idx_oauth_user', 'oauth_user_id', NULL, NULL); -- 授权账号
CALL cgch_add_index('channel_mail_message', 'idx_account_folder_sent', 'account_id', 'folder', 'sent_time'); -- 收件箱 / 已发送列表
CALL cgch_add_index('channel_mail_message', 'idx_account_message_id', 'account_id', 'message_id', NULL); -- 收信时按邮件编号查重
CALL cgch_add_index('channel_mail_message', 'idx_account_uid', 'account_id', 'imap_uid', NULL); -- 收信时按 IMAP 编号查重
CALL cgch_add_index('channel_mail_message', 'idx_thread', 'thread_key', NULL, NULL); -- 邮件往来线程
CALL cgch_add_index('channel_mail_message', 'idx_customer_sent', 'customer_id', 'sent_time', NULL); -- 客户的邮件往来
CALL cgch_add_index('channel_mail_message', 'idx_sent_time', 'sent_time', NULL, NULL); -- 看板、报告按时间统计
CALL cgch_add_index('channel_mail_attachment', 'idx_message', 'message_id', NULL, NULL); -- 邮件的附件
CALL cgch_add_index('channel_mail_account', 'idx_user', 'user_id', NULL, NULL); -- 业务员自己的邮箱
CALL cgch_add_index('channel_mail_account', 'idx_email', 'email', NULL, NULL); -- 按邮箱地址查找
CALL cgch_add_index('channel_ai_draft', 'idx_channel_ref', 'channel', 'ref_id', NULL); -- 消息对应的 AI 草稿
CALL cgch_add_index('channel_ai_draft', 'idx_status_create', 'status', 'create_time', NULL); -- 待处理草稿
CALL cgch_add_index('channel_ai_draft', 'idx_create_time', 'create_time', NULL, NULL); -- 看板按时间统计
CALL cgch_add_index('channel_ai_deferred', 'idx_status_due', 'status', 'due_time', NULL); -- 到点执行的延后回复
CALL cgch_add_index('channel_ai_deferred', 'idx_channel_ref', 'channel', 'ref_id', NULL); -- 消息对应的延后回复
CALL cgch_add_index('channel_credit_ledger', 'idx_create_time', 'create_time', NULL, NULL); -- 智点流水按时间查询
CALL cgch_add_index('channel_credit_ledger', 'idx_user_time', 'user_id', 'create_time', NULL); -- 业务员的智点流水
CALL cgch_add_index('channel_credit_order', 'idx_pay_order', 'pay_order_id', NULL, NULL); -- 支付回调找到订单
CALL cgch_add_index('channel_reactivate_item', 'idx_task', 'task_id', NULL, NULL); -- 唤醒任务的明细
CALL cgch_add_index('channel_reactivate_item', 'idx_channel_sent_ref', 'channel', 'sent_ref_id', NULL); -- 客户回复后找到唤醒记录
CALL cgch_add_index('channel_reactivate_item', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的唤醒记录
CALL cgch_add_index('channel_suppression', 'idx_channel_address', 'channel', 'address', NULL); -- 发送前检查退订 / 黑名单
CALL cgch_add_index('channel_assistant_message', 'idx_chat', 'chat_id', NULL, NULL); -- CRMClaw 对话消息
CALL cgch_add_index('channel_assistant_chat', 'idx_user_update', 'user_id', 'update_time', NULL); -- CRMClaw 对话列表
CALL cgch_add_index('channel_cloud_file', 'idx_parent', 'parent_id', NULL, NULL); -- 云盘目录
CALL cgch_add_index('channel_cloud_file', 'idx_owner', 'owner_user_id', NULL, NULL); -- 个人云盘
CALL cgch_add_index('channel_ai_knowledge', 'idx_file', 'file_id', NULL, NULL); -- 知识文件拆出的条目
CALL cgch_add_index('channel_research', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的背调
CALL cgch_add_index('channel_research', 'idx_clue', 'clue_id', NULL, NULL); -- 线索的背调
CALL cgch_add_index('channel_ai_report', 'idx_period', 'period_type', 'period_start', NULL); -- 日报 / 周报
CALL cgch_add_index('channel_ai_case', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的成交案例
CALL cgch_add_index('crm_customer', 'idx_email', 'email', NULL, NULL); -- 收到消息时按邮箱匹配客户
CALL cgch_add_index('crm_customer', 'idx_whatsapp', 'whatsapp', NULL, NULL); -- 收到消息时按 WhatsApp 号码匹配客户
CALL cgch_add_index('crm_customer', 'idx_mobile', 'mobile', NULL, NULL); -- 按手机号匹配客户
CALL cgch_add_index('crm_customer', 'idx_owner', 'owner_user_id', NULL, NULL); -- 我的客户
CALL cgch_add_index('crm_customer', 'idx_contact_next', 'contact_next_time', NULL, NULL); -- 待跟进客户
CALL cgch_add_index('crm_clue', 'idx_email', 'email', NULL, NULL); -- 按邮箱匹配线索
CALL cgch_add_index('crm_clue', 'idx_whatsapp', 'whatsapp', NULL, NULL); -- 按 WhatsApp 号码匹配线索
CALL cgch_add_index('crm_clue', 'idx_mobile', 'mobile', NULL, NULL); -- 按手机号匹配线索
CALL cgch_add_index('crm_clue', 'idx_owner', 'owner_user_id', NULL, NULL); -- 我的线索
CALL cgch_add_index('crm_contact', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的联系人
CALL cgch_add_index('crm_contact', 'idx_email', 'email', NULL, NULL); -- 按邮箱匹配联系人
CALL cgch_add_index('crm_contact', 'idx_mobile', 'mobile', NULL, NULL); -- 按手机号匹配联系人
CALL cgch_add_index('crm_business', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的商机
CALL cgch_add_index('crm_contract', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的合同
CALL cgch_add_index('crm_receivable', 'idx_customer', 'customer_id', NULL, NULL); -- 客户的回款
CALL cgch_add_index('crm_receivable', 'idx_contract', 'contract_id', NULL, NULL); -- 合同的回款
CALL cgch_add_index('crm_follow_up_record', 'idx_biz', 'biz_type', 'biz_id', NULL); -- 客户 / 线索 / 商机的跟进记录
CALL cgch_add_index('crm_permission', 'idx_biz', 'biz_type', 'biz_id', NULL); -- 数据权限
CALL cgch_add_index('crm_permission', 'idx_user', 'user_id', NULL, NULL); -- 我负责 / 参与的数据
DROP PROCEDURE cgch_add_index;
