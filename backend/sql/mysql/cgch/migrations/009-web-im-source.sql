-- 官网 IM：客户来源字典补充（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_source' AND `value` = '17';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`) VALUES
  (17, '官网IM', '17', 'crm_customer_source', 0, 'primary', '', 'CGCH 官网在线聊天', '1', '1', b'0');
