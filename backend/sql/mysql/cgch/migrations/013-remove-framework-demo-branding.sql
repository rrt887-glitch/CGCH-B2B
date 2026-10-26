-- 去掉框架演示数据里的「芋道」品牌信息（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
UPDATE `system_users` SET `nickname` = '管理员' WHERE `id` = 1 AND `nickname` = '芋道源码';
UPDATE `system_users` SET `email` = '' WHERE `id` = 1 AND `email` = '13aoteman@126.com';
UPDATE `system_users` SET `mobile` = '' WHERE `id` = 1 AND `mobile` = '18818260272';
UPDATE `system_users` SET `avatar` = '' WHERE `avatar` LIKE '%iocoder.cn%';
UPDATE `system_users` SET `nickname` = '演示用户' WHERE `id` <> 1 AND `nickname` LIKE '芋道%';
UPDATE `system_users` SET `email` = '' WHERE `email` LIKE '%iocoder.cn';
UPDATE `system_dept` SET `name` = '草根出海', `phone` = '', `email` = '' WHERE `id` = 100 AND `name` = '芋道源码';
UPDATE `system_tenant` SET `contact_name` = '演示' WHERE `id` <> 1 AND `contact_name` LIKE '芋道%';
UPDATE `system_tenant` SET `websites` = '' WHERE `id` <> 1 AND `websites` LIKE '%iocoder.cn%';
UPDATE `system_oauth2_client` SET `name` = '草根出海', `logo` = '', `description` = '' WHERE `client_id` = 'default' AND `name` = '芋道源码';
UPDATE `system_oauth2_client` SET `logo` = '' WHERE `logo` LIKE '%iocoder.cn%';
DELETE FROM `system_notice` WHERE `title` LIKE '%芋道%' OR `content` LIKE '%iocoder.cn%';
DELETE FROM `system_notify_message` WHERE `template_params` LIKE '%芋道%';
