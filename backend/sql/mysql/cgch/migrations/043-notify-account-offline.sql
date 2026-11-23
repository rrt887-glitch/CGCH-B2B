-- 043：新增「渠道掉线」通知模板（Unipile 托管账号被平台踢下线时提醒负责人重新授权）
-- 可重复执行。

INSERT INTO `system_notify_template`
    (`name`, `code`, `nickname`, `content`, `type`, `params`, `status`, `remark`, `creator`, `updater`)
SELECT '渠道掉线', 'cgch_account_offline', 'CGCH 数字员工',
       '「{account}」的渠道连接已断开，暂时收不到新消息，请重新授权', 1, '["account"]', 0,
       'LinkedIn/Facebook/Instagram/Telegram 托管账号被平台踢下线', '1', '1'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `system_notify_template` WHERE `code` = 'cgch_account_offline');
