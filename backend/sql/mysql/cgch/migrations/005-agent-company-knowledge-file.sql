-- 智能体：企业信息、知识库资料文件（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
ALTER TABLE `channel_ai_config` ADD COLUMN `company_json` mediumtext NULL COMMENT '企业信息（JSON）：公司名称、行业、官网、简介';
ALTER TABLE `channel_ai_knowledge` ADD COLUMN `file_id` bigint NULL COMMENT '来源资料文件编号（type 为 file 时）';
CREATE TABLE IF NOT EXISTS `channel_ai_knowledge_file` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '文件名',
  `url` varchar(255) NULL COMMENT '文件访问地址',
  `size` bigint NULL COMMENT '文件大小（字节）',
  `file_type` varchar(255) NULL COMMENT '文件类型（扩展名，例如 pdf、docx）',
  `status` varchar(255) NULL COMMENT '学习进度：learning 学习中、done 学习完成、failed 学习失败',
  `chunks` int NULL COMMENT '拆出的知识条目数',
  `error_msg` varchar(255) NULL COMMENT '学习失败的原因',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库资料文件：上传后解析成文字、按段落拆成知识条目供 AI 检索';
