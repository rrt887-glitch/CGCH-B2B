SET NAMES utf8mb4;
-- 客户级别改为原网站的 A/B/C/D 四类（AI 按「配技能 → CRM 自动录入 → 客户评级」规则打级）
UPDATE `system_dict_data` SET `label` = 'A类客户', `remark` = '成熟的品牌客户' WHERE `dict_type` = 'crm_customer_level' AND `value` = '1';
UPDATE `system_dict_data` SET `label` = 'B类客户', `remark` = '初创的品牌客户' WHERE `dict_type` = 'crm_customer_level' AND `value` = '2';
UPDATE `system_dict_data` SET `label` = 'C类客户', `remark` = '创业者' WHERE `dict_type` = 'crm_customer_level' AND `value` = '3';
DELETE FROM `system_dict_data` WHERE `dict_type` = 'crm_customer_level' AND `value` = '4';
INSERT INTO `system_dict_data` (`sort`, `label`, `value`, `dict_type`, `status`, `color_type`, `css_class`, `remark`, `creator`, `updater`, `deleted`)
VALUES (4, 'D类客户', '4', 'crm_customer_level', 0, 'default', '', '自用', '1', '1', b'0');
