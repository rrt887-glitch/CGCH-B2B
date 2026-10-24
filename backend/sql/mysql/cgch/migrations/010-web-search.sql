-- 允许联网报价：联网搜索接口与密钥（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
ALTER TABLE `channel_ai_config` ADD COLUMN `search_base_url` varchar(255) NULL COMMENT '联网搜索接口地址（Tavily 兼容），为空时使用 https://api.tavily.com；用于「允许联网报价」' AFTER `embedding_model`;
ALTER TABLE `channel_ai_config` ADD COLUMN `search_api_key` varchar(1024) NULL COMMENT '联网搜索 API Key，加密存储' AFTER `search_base_url`;
