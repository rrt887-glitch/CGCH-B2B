-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `ai_chat_conversation`;
CREATE TABLE `ai_chat_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `title` varchar(255) NULL COMMENT '对话标题',
  `pinned` bit(1) NULL DEFAULT b'0' COMMENT '是否置顶',
  `pinned_time` datetime NULL COMMENT '置顶时间',
  `role_id` bigint NULL COMMENT '角色编号',
  `model_id` bigint NULL COMMENT '模型编号',
  `model` varchar(255) NULL COMMENT '模型标志',
  `system_message` varchar(255) NULL COMMENT '角色设定',
  `temperature` varchar(255) NULL COMMENT '温度参数',
  `max_tokens` int NULL COMMENT '单条回复的最大 Token 数量',
  `max_contexts` int NULL COMMENT '上下文的最大 Message 数量',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI Chat 对话 DO';

DROP TABLE IF EXISTS `ai_chat_message`;
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `conversation_id` bigint NULL COMMENT '对话编号',
  `reply_id` bigint NULL COMMENT '回复消息编号',
  `type` varchar(255) NULL COMMENT '消息类型',
  `user_id` bigint NULL COMMENT '用户编号',
  `role_id` bigint NULL COMMENT '角色编号',
  `model` varchar(255) NULL COMMENT '模型标志',
  `model_id` bigint NULL COMMENT '模型编号',
  `content` varchar(1024) NULL COMMENT '聊天内容',
  `reasoning_content` varchar(255) NULL COMMENT '推理内容',
  `use_context` bit(1) NULL DEFAULT b'0' COMMENT '是否携带上下文',
  `segment_ids` varchar(2000) NULL COMMENT '知识库段落编号数组',
  `attachment_urls` varchar(2000) NULL COMMENT '联网搜索的网页内容数组',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI Chat 消息 DO';

DROP TABLE IF EXISTS `ai_image`;
CREATE TABLE `ai_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `prompt` varchar(255) NULL COMMENT '提示词',
  `platform` varchar(255) NULL COMMENT '平台',
  `model_id` bigint NULL COMMENT '模型编号',
  `model` varchar(255) NULL COMMENT '模型标识',
  `width` int NULL COMMENT '图片宽度',
  `height` int NULL COMMENT '图片高度',
  `status` int NULL COMMENT '生成状态',
  `finish_time` datetime NULL COMMENT '完成时间',
  `error_message` varchar(255) NULL COMMENT '绘画错误信息',
  `pic_url` varchar(255) NULL COMMENT '图片地址',
  `public_status` bit(1) NULL DEFAULT b'0' COMMENT '是否公开',
  `options` varchar(255) NULL COMMENT '绘制参数，不同 platform 的不同参数',
  `task_id` varchar(255) NULL COMMENT 'mj buttons 按钮',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 绘画 DO';

DROP TABLE IF EXISTS `ai_knowledge`;
CREATE TABLE `ai_knowledge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '知识库名称',
  `description` varchar(1024) NULL COMMENT '知识库描述',
  `embedding_model_id` bigint NULL COMMENT '向量模型编号',
  `embedding_model` varchar(255) NULL COMMENT '模型标识',
  `top_k` int NULL COMMENT 'topK',
  `similarity_threshold` varchar(255) NULL COMMENT '相似度阈值',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识库 DO';

DROP TABLE IF EXISTS `ai_knowledge_document`;
CREATE TABLE `ai_knowledge_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `knowledge_id` bigint NULL COMMENT '知识库编号',
  `name` varchar(255) NULL COMMENT '文档名称',
  `url` varchar(255) NULL COMMENT '文件 URL',
  `content` varchar(1024) NULL COMMENT '内容',
  `content_length` int NULL COMMENT '文档长度',
  `tokens` int NULL COMMENT '文档 token 数量',
  `segment_max_tokens` int NULL COMMENT '分片最大 Token 数',
  `retrieval_count` int NULL COMMENT '召回次数',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识库-文档 DO';

DROP TABLE IF EXISTS `ai_knowledge_segment`;
CREATE TABLE `ai_knowledge_segment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `knowledge_id` bigint NULL COMMENT '知识库编号',
  `document_id` bigint NULL COMMENT '文档编号',
  `content` varchar(1024) NULL COMMENT '切片内容',
  `content_length` int NULL COMMENT '切片内容长度',
  `vector_id` varchar(255) NULL COMMENT '向量库的编号',
  `tokens` int NULL COMMENT 'token 数量',
  `retrieval_count` int NULL COMMENT '召回次数',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 知识库-文档分段 DO';

DROP TABLE IF EXISTS `ai_mind_map`;
CREATE TABLE `ai_mind_map` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `platform` varchar(255) NULL COMMENT '平台',
  `model_id` bigint NULL COMMENT '模型编号',
  `model` varchar(255) NULL COMMENT '模型',
  `prompt` varchar(255) NULL COMMENT '生成内容提示',
  `generated_content` varchar(255) NULL COMMENT '生成的内容',
  `error_message` varchar(255) NULL COMMENT '错误信息',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 思维导图 DO';

DROP TABLE IF EXISTS `ai_api_key`;
CREATE TABLE `ai_api_key` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '名称',
  `api_key` varchar(255) NULL COMMENT '密钥',
  `platform` varchar(255) NULL COMMENT '平台',
  `url` varchar(255) NULL COMMENT 'API 地址',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI API 秘钥 DO';

DROP TABLE IF EXISTS `ai_chat_role`;
CREATE TABLE `ai_chat_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '角色名称',
  `avatar` varchar(255) NULL COMMENT '角色头像',
  `category` varchar(255) NULL COMMENT '角色分类',
  `description` varchar(1024) NULL COMMENT '角色描述',
  `system_message` varchar(255) NULL COMMENT '角色设定',
  `user_id` bigint NULL COMMENT '用户编号',
  `model_id` bigint NULL COMMENT '模型编号',
  `knowledge_ids` varchar(2000) NULL COMMENT '引用的知识库编号列表',
  `tool_ids` varchar(2000) NULL COMMENT '引用的工具编号列表',
  `mcp_client_names` varchar(2000) NULL COMMENT '引用的 MCP Client 名字列表',
  `public_status` bit(1) NULL DEFAULT b'0' COMMENT '是否公开',
  `sort` int NULL COMMENT '排序值',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 聊天角色 DO';

DROP TABLE IF EXISTS `ai_model`;
CREATE TABLE `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `key_id` bigint NULL COMMENT 'API 秘钥编号',
  `name` varchar(255) NULL COMMENT '模型名称',
  `model` varchar(255) NULL COMMENT '模型标志',
  `platform` varchar(255) NULL COMMENT '平台',
  `type` int NULL COMMENT '类型',
  `sort` int NULL COMMENT '排序值',
  `status` int NULL COMMENT '状态',
  `temperature` varchar(255) NULL COMMENT '温度参数',
  `max_tokens` int NULL COMMENT '单条回复的最大 Token 数量',
  `max_contexts` int NULL COMMENT '上下文的最大 Message 数量',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 模型 DO';

DROP TABLE IF EXISTS `ai_tool`;
CREATE TABLE `ai_tool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '工具名称',
  `description` varchar(1024) NULL COMMENT '工具描述',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 工具 DO';

DROP TABLE IF EXISTS `ai_music`;
CREATE TABLE `ai_music` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `title` varchar(255) NULL COMMENT '音乐名称',
  `lyric` varchar(255) NULL COMMENT '歌词',
  `image_url` varchar(255) NULL COMMENT '图片地址',
  `audio_url` varchar(255) NULL COMMENT '音频地址',
  `video_url` varchar(255) NULL COMMENT '视频地址',
  `status` int NULL COMMENT '音乐状态',
  `generate_mode` int NULL COMMENT '生成模式',
  `description` varchar(1024) NULL COMMENT '描述词',
  `platform` varchar(255) NULL COMMENT '平台',
  `model` varchar(255) NULL COMMENT '模型',
  `tags` varchar(2000) NULL COMMENT '音乐风格标签',
  `duration` varchar(255) NULL COMMENT '音乐时长',
  `public_status` bit(1) NULL DEFAULT b'0' COMMENT '是否公开',
  `task_id` varchar(255) NULL COMMENT '任务编号',
  `error_message` varchar(255) NULL COMMENT '错误信息',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 音乐 DO';

DROP TABLE IF EXISTS `ai_workflow`;
CREATE TABLE `ai_workflow` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '工作流名称',
  `code` varchar(255) NULL COMMENT '工作流标识',
  `graph` varchar(255) NULL COMMENT '工作流模型 JSON 数据',
  `remark` varchar(1024) NULL COMMENT '备注',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 工作流 DO';

DROP TABLE IF EXISTS `ai_write`;
CREATE TABLE `ai_write` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `type` int NULL COMMENT '写作类型',
  `platform` varchar(255) NULL COMMENT '平台',
  `model_id` bigint NULL COMMENT '模型编号',
  `model` varchar(255) NULL COMMENT '模型',
  `prompt` varchar(255) NULL COMMENT '生成内容提示',
  `generated_content` varchar(255) NULL COMMENT '生成的内容',
  `original_content` varchar(255) NULL COMMENT '原文',
  `length` int NULL COMMENT '长度提示词',
  `format` int NULL COMMENT '格式提示词',
  `tone` int NULL COMMENT '语气提示词',
  `language` int NULL COMMENT '语言提示词',
  `error_message` varchar(255) NULL COMMENT '错误信息',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 写作 DO';
