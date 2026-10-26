-- YouTube、TikTok 评论渠道：客户来源字典补充（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` IN ('18', '19');
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`) VALUES
  (18, 'YouTube', '18', 'crm_customer_source', 0, 'danger', '', 'CGCH 视频评论', '1', '1', b'0'),
  (19, 'TikTok', '19', 'crm_customer_source', 0, 'default', '', 'CGCH 视频评论', '1', '1', b'0');
