-- 扫码接入时 WhatsApp 官方系统账号（0@s.whatsapp.net 等）发来的通知被当成了客户会话，清掉（已有数据库执行一次）
SET NAMES utf8mb4;
DELETE m FROM `channel_wa_message` m
  JOIN `channel_wa_conversation` c ON c.`id` = m.`conversation_id`
  JOIN `channel_wa_account` a ON a.`id` = c.`account_id`
 WHERE a.`auth_type` = 'evolution' AND c.`platform` = 'whatsapp' AND CHAR_LENGTH(c.`wa_id`) < 6;
DELETE c FROM `channel_wa_conversation` c
  JOIN `channel_wa_account` a ON a.`id` = c.`account_id`
 WHERE a.`auth_type` = 'evolution' AND c.`platform` = 'whatsapp' AND CHAR_LENGTH(c.`wa_id`) < 6;
