-- 对方没设置昵称时 WhatsApp 用号码 / 隐藏编号代替昵称，这种不当作昵称；电脑端看不到的消息换成说明（可重复执行）
SET NAMES utf8mb4;
UPDATE `channel_wa_conversation` SET `profile_name` = NULL
  WHERE (`platform` = 'whatsapp' OR `platform` IS NULL) AND `profile_name` REGEXP '^[0-9 +()-]+$';
UPDATE `channel_wa_message` SET `text_body` = '[这条消息在电脑端看不到，请在手机 WhatsApp 上查看]'
  WHERE `type` = 'placeholderMessage';
UPDATE `channel_wa_conversation` SET `last_message_preview` = '[这条消息在电脑端看不到，请在手机 WhatsApp 上查看]'
  WHERE `last_message_preview` LIKE '[暂不支持的消息类型：placeholderMessage]%';
