-- 智能体五步配置（技能、转人工、开场白、学习真人销售）与「需要支持」分类（已有数据库执行一次；新装数据库已包含，不会执行）
SET NAMES utf8mb4;
ALTER TABLE `channel_ai_config`
  ADD COLUMN `skills_json` mediumtext NULL COMMENT '智能体技能配置（JSON，见 ChannelAgentSettings.Skills）',
  ADD COLUMN `handoff_json` mediumtext NULL COMMENT '转人工配置（JSON，见 ChannelAgentSettings.Handoff）',
  ADD COLUMN `opening_json` mediumtext NULL COMMENT '固定开场白（JSON，见 ChannelAgentSettings.Opening）',
  ADD COLUMN `learn_json` mediumtext NULL COMMENT '学习真人销售：学习范围与目标（JSON，见 ChannelAgentSettings.Learn）',
  ADD COLUMN `sales_style` mediumtext NULL COMMENT '从真人销售对话中学到的话术要点';
ALTER TABLE `channel_ai_draft`
  ADD COLUMN `support_type` varchar(255) NULL COMMENT '需要人工支持的类型：handoff、missing、permission、crisis、compliance、other';
