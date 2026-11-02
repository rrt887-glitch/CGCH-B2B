-- 改为没有头像资料时单独向 WhatsApp 查询：之前没取到头像的会话清空获取时间，升级后重新获取（可重复执行）
SET NAMES utf8mb4;
UPDATE `channel_wa_conversation` SET `avatar_time` = NULL WHERE `avatar_url` IS NULL AND `avatar_time` IS NOT NULL;
