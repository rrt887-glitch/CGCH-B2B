-- 云盘文件（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS `channel_cloud_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `scope` varchar(255) NULL COMMENT '所属云盘：enterprise 企业云盘、personal 个人云盘',
  `owner_user_id` bigint NULL COMMENT '个人云盘的所属用户',
  `parent_id` bigint NULL COMMENT '上级文件夹编号，根目录为 0',
  `folder` bit(1) NULL DEFAULT b'0' COMMENT '是否为文件夹',
  `name` varchar(255) NULL COMMENT '名称',
  `description` varchar(1024) NULL COMMENT '描述（AI 据此判断什么时候发送这个文件）',
  `url` varchar(255) NULL COMMENT '文件访问地址',
  `size` bigint NULL COMMENT '文件大小（字节）',
  `mime_type` varchar(255) NULL COMMENT '文件类型',
  `ai_sendable` bit(1) NULL DEFAULT b'0' COMMENT '是否允许 AI 发给客户（仅企业云盘的文件）',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '云盘文件：企业云盘（全员可见，AI 可发给客户）与个人云盘（只有本人可见），支持文件夹';
