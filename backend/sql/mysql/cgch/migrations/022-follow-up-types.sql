-- 跟进方式加上外贸常用的 WhatsApp、邮件、视频会议（已有数据库执行一次，可重复执行）
SET NAMES utf8mb4;
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
SELECT s.sort, s.label, s.value, 'crm_follow_up_type', 0, '', '', '', '1', '1', b'0' FROM (
  SELECT 5 AS sort, 'WhatsApp' AS label, '5' AS value UNION ALL
  SELECT 6, '邮件', '6' UNION ALL
  SELECT 7, '视频会议', '7') s
WHERE NOT EXISTS (SELECT 1 FROM `system_dict_data` d WHERE d.`dict_type` = 'crm_follow_up_type' AND d.`value` = s.value AND d.`deleted` = b'0');
