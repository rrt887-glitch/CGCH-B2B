-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `bpm_category`;
CREATE TABLE `bpm_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '分类名',
  `code` varchar(255) NULL COMMENT '分类标志',
  `description` varchar(1024) NULL COMMENT '分类描述',
  `status` int NULL COMMENT '分类状态',
  `sort` int NULL COMMENT '分类排序',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BPM 流程分类 DO';

DROP TABLE IF EXISTS `bpm_form`;
CREATE TABLE `bpm_form` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '表单名',
  `status` int NULL COMMENT '状态',
  `conf` varchar(255) NULL COMMENT '表单的配置',
  `fields` varchar(2000) NULL COMMENT '表单项的数组',
  `remark` varchar(1024) NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BPM 工作流的表单定义';

DROP TABLE IF EXISTS `bpm_process_definition_info`;
CREATE TABLE `bpm_process_definition_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `process_definition_id` varchar(255) NULL COMMENT '流程定义的编号',
  `model_id` varchar(255) NULL COMMENT '流程模型的编号',
  `model_type` int NULL COMMENT '流程模型的类型',
  `category` varchar(255) NULL COMMENT '流程分类的编码',
  `icon` varchar(255) NULL COMMENT '图标',
  `description` varchar(1024) NULL COMMENT '描述',
  `form_type` int NULL COMMENT '表单类型',
  `form_id` bigint NULL COMMENT '动态表单编号',
  `form_conf` varchar(255) NULL COMMENT '表单的配置',
  `form_fields` varchar(2000) NULL COMMENT '表单项的数组',
  `form_custom_create_path` varchar(255) NULL COMMENT '自定义表单的提交路径，使用 Vue 的路由地址',
  `form_custom_view_path` varchar(255) NULL COMMENT '自定义表单的查看路径，使用 Vue 的路由地址',
  `simple_model` varchar(255) NULL COMMENT 'SIMPLE 设计器模型数据 json 格式',
  `visible` bit(1) NULL DEFAULT b'0' COMMENT '是否可见',
  `sort` bigint NULL COMMENT '排序值',
  `start_dept_ids` varchar(2000) NULL COMMENT '可发起用户编号数组',
  `allow_cancel_running_process` bit(1) NULL DEFAULT b'0' COMMENT '可管理用户编号数组',
  `allow_withdraw_task` bit(1) NULL DEFAULT b'0' COMMENT '是否允许审批人撤回任务',
  `auto_approval_type` int NULL COMMENT '流程 ID 规则',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BPM 流程定义的拓信息';

DROP TABLE IF EXISTS `bpm_process_expression`;
CREATE TABLE `bpm_process_expression` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '表达式名字',
  `status` int NULL COMMENT '表达式状态',
  `expression` varchar(255) NULL COMMENT '表达式',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BPM 流程表达式 DO';

DROP TABLE IF EXISTS `bpm_process_listener`;
CREATE TABLE `bpm_process_listener` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '监听器名字',
  `status` int NULL COMMENT '状态',
  `type` varchar(255) NULL COMMENT '监听类型',
  `event` varchar(255) NULL COMMENT '监听事件',
  `value_type` varchar(255) NULL COMMENT '值类型',
  `value` varchar(255) NULL COMMENT '值',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BPM 流程监听器 DO';

DROP TABLE IF EXISTS `bpm_user_group`;
CREATE TABLE `bpm_user_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '组名',
  `description` varchar(1024) NULL COMMENT '描述',
  `status` int NULL COMMENT '状态',
  `user_ids` varchar(2000) NULL COMMENT '成员用户编号数组',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'BPM 用户组';

DROP TABLE IF EXISTS `bpm_oa_leave`;
CREATE TABLE `bpm_oa_leave` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '申请人的用户编号',
  `type` int NULL COMMENT '请假类型',
  `reason` varchar(255) NULL COMMENT '原因',
  `start_time` datetime NULL COMMENT '开始时间',
  `end_time` datetime NULL COMMENT '结束时间',
  `day` bigint NULL COMMENT '请假天数',
  `status` int NULL COMMENT '审批结果',
  `process_instance_id` varchar(255) NULL COMMENT '对应的流程编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'OA 请假申请 DO';

DROP TABLE IF EXISTS `bpm_process_instance_copy`;
CREATE TABLE `bpm_process_instance_copy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `start_user_id` bigint NULL COMMENT '发起人 Id',
  `process_instance_name` varchar(255) NULL COMMENT '流程名',
  `process_instance_id` varchar(255) NULL COMMENT '流程实例的编号',
  `process_definition_id` varchar(255) NULL COMMENT '流程实例的流程定义编号',
  `category` varchar(255) NULL COMMENT '流程分类',
  `activity_id` varchar(255) NULL COMMENT '流程活动的编号',
  `activity_name` varchar(255) NULL COMMENT '流程活动的名字',
  `task_id` varchar(255) NULL COMMENT '流程活动的编号',
  `user_id` bigint NULL COMMENT '用户编号（被抄送的用户编号）',
  `reason` varchar(255) NULL COMMENT '抄送意见',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '流程抄送 DO';
