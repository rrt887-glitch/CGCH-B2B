-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `crm_business`;
CREATE TABLE `crm_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '商机名称',
  `customer_id` bigint NULL COMMENT '客户编号',
  `follow_up_status` bit(1) NULL DEFAULT b'0' COMMENT '跟进状态',
  `contact_last_time` datetime NULL COMMENT '最后跟进时间',
  `contact_next_time` datetime NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NULL COMMENT '负责人的用户编号',
  `status_type_id` bigint NULL COMMENT '商机状态组编号',
  `status_id` bigint NULL COMMENT '商机状态编号',
  `end_status` int NULL COMMENT '结束状态',
  `end_remark` varchar(255) NULL COMMENT '结束时的备注',
  `deal_time` datetime NULL COMMENT '预计成交日期',
  `total_product_price` decimal(24, 6) NULL COMMENT '产品总金额，单位：元',
  `discount_percent` decimal(24, 6) NULL COMMENT '整单折扣，百分比',
  `total_price` decimal(24, 6) NULL COMMENT '商机总金额，单位：元',
  `remark` varchar(1024) NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 商机 DO';

DROP TABLE IF EXISTS `crm_business_product`;
CREATE TABLE `crm_business_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `business_id` bigint NULL COMMENT '商机编号',
  `product_id` bigint NULL COMMENT '产品编号',
  `product_price` decimal(24, 6) NULL COMMENT '产品单价，单位：元',
  `business_price` decimal(24, 6) NULL COMMENT '商机价格, 单位：元',
  `count` decimal(24, 6) NULL COMMENT '数量',
  `total_price` decimal(24, 6) NULL COMMENT '总计价格，单位：元',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 商机产品关联表 DO';

DROP TABLE IF EXISTS `crm_business_status`;
CREATE TABLE `crm_business_status` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type_id` bigint NULL COMMENT '状态类型编号',
  `name` varchar(255) NULL COMMENT '状态名',
  `percent` int NULL COMMENT '赢单率，百分比',
  `sort` int NULL COMMENT '排序',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 商机状态 DO';

DROP TABLE IF EXISTS `crm_business_status_type`;
CREATE TABLE `crm_business_status_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '状态类型名',
  `dept_ids` varchar(2000) NULL COMMENT '使用的部门编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 商机状态组 DO';

DROP TABLE IF EXISTS `crm_clue`;
CREATE TABLE `crm_clue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '线索名称',
  `follow_up_status` bit(1) NULL DEFAULT b'0' COMMENT '跟进状态',
  `contact_last_time` datetime NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(1024) NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NULL COMMENT '负责人的用户编号',
  `transform_status` bit(1) NULL DEFAULT b'0' COMMENT '转化状态',
  `customer_id` bigint NULL COMMENT '客户编号',
  `mobile` varchar(255) NULL COMMENT '手机号',
  `telephone` varchar(255) NULL COMMENT '电话',
  `qq` varchar(255) NULL COMMENT 'QQ',
  `wechat` varchar(255) NULL COMMENT 'wechat',
  `email` varchar(255) NULL COMMENT 'email',
  `area_id` int NULL COMMENT '所在地',
  `detail_address` varchar(1024) NULL COMMENT '详细地址',
  `industry_id` int NULL COMMENT '所属行业',
  `level` int NULL COMMENT '客户等级',
  `source` int NULL COMMENT '客户来源',
  `remark` varchar(1024) NULL COMMENT '备注',
  `country_code` varchar(255) NULL COMMENT '国家/地区，ISO 3166-1 二字码，例如 US、DE',
  `company_name` varchar(255) NULL COMMENT '公司名称',
  `website` varchar(255) NULL COMMENT '公司官网',
  `whatsapp` varchar(255) NULL COMMENT 'WhatsApp 号码，含国际区号',
  `group_id` bigint NULL COMMENT '客户分组编号，关联 crm_customer_group',
  `tag_ids` varchar(2000) NULL COMMENT '标签编号列表，关联 crm_tag',
  `ai_hosted` bit(1) NULL DEFAULT b'0' COMMENT '是否由 AI 数字员工托管接待',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 线索 DO';

DROP TABLE IF EXISTS `crm_contact_business`;
CREATE TABLE `crm_contact_business` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contact_id` bigint NULL COMMENT '联系人编号',
  `business_id` bigint NULL COMMENT '商机编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 联系人与商机的关联 DO';

DROP TABLE IF EXISTS `crm_contact`;
CREATE TABLE `crm_contact` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '联系人姓名',
  `customer_id` bigint NULL COMMENT '客户编号',
  `contact_last_time` datetime NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(1024) NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NULL COMMENT '负责人用户编号',
  `mobile` varchar(255) NULL COMMENT '手机号',
  `telephone` varchar(255) NULL COMMENT '电话',
  `email` varchar(255) NULL COMMENT '电子邮箱',
  `qq` bigint NULL COMMENT 'QQ',
  `wechat` varchar(255) NULL COMMENT '微信',
  `area_id` int NULL COMMENT '所在地',
  `detail_address` varchar(1024) NULL COMMENT '详细地址',
  `sex` int NULL COMMENT '性别',
  `master` bit(1) NULL DEFAULT b'0' COMMENT '是否关键决策人',
  `post` varchar(255) NULL COMMENT '职位',
  `parent_id` bigint NULL COMMENT '直属上级',
  `remark` varchar(1024) NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 联系人 DO';

DROP TABLE IF EXISTS `crm_contract_config`;
CREATE TABLE `crm_contract_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `notify_enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否开启提前提醒',
  `notify_days` int NULL COMMENT '提前提醒天数',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '';

DROP TABLE IF EXISTS `crm_contract`;
CREATE TABLE `crm_contract` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '合同名称',
  `no` varchar(255) NULL COMMENT '合同编号',
  `customer_id` bigint NULL COMMENT '客户编号',
  `business_id` bigint NULL COMMENT '商机编号，非必须',
  `contact_last_time` datetime NULL COMMENT '最后跟进时间',
  `owner_user_id` bigint NULL COMMENT '负责人的用户编号',
  `process_instance_id` varchar(255) NULL COMMENT '工作流编号',
  `audit_status` int NULL COMMENT '审批状态',
  `order_date` datetime NULL COMMENT '下单日期',
  `start_time` datetime NULL COMMENT '开始时间',
  `end_time` datetime NULL COMMENT '结束时间',
  `total_product_price` decimal(24, 6) NULL COMMENT '产品总金额，单位：元',
  `discount_percent` decimal(24, 6) NULL COMMENT '整单折扣',
  `total_price` decimal(24, 6) NULL COMMENT '合同总金额，单位：分',
  `sign_contact_id` bigint NULL COMMENT '客户签约人，非必须',
  `sign_user_id` bigint NULL COMMENT '公司签约人，非必须',
  `remark` varchar(1024) NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 合同 DO';

DROP TABLE IF EXISTS `crm_contract_product`;
CREATE TABLE `crm_contract_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `contract_id` bigint NULL COMMENT '合同编号',
  `product_id` bigint NULL COMMENT '产品编号',
  `product_price` decimal(24, 6) NULL COMMENT '产品单价，单位：元',
  `contract_price` decimal(24, 6) NULL COMMENT '合同价格, 单位：元',
  `count` decimal(24, 6) NULL COMMENT '数量',
  `total_price` decimal(24, 6) NULL COMMENT '总计价格，单位：元',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 合同产品关联表 DO';

DROP TABLE IF EXISTS `crm_customer`;
CREATE TABLE `crm_customer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '客户名称',
  `follow_up_status` bit(1) NULL DEFAULT b'0' COMMENT '跟进状态',
  `contact_last_time` datetime NULL COMMENT '最后跟进时间',
  `contact_last_content` varchar(1024) NULL COMMENT '最后跟进内容',
  `contact_next_time` datetime NULL COMMENT '下次联系时间',
  `owner_user_id` bigint NULL COMMENT '负责人的用户编号',
  `owner_time` datetime NULL COMMENT '成为负责人的时间',
  `lock_status` bit(1) NULL DEFAULT b'0' COMMENT '锁定状态',
  `deal_status` bit(1) NULL DEFAULT b'0' COMMENT '成交状态',
  `mobile` varchar(255) NULL COMMENT '手机',
  `telephone` varchar(255) NULL COMMENT '电话',
  `qq` varchar(255) NULL COMMENT 'QQ',
  `wechat` varchar(255) NULL COMMENT 'wechat',
  `email` varchar(255) NULL COMMENT 'email',
  `area_id` int NULL COMMENT '所在地',
  `detail_address` varchar(1024) NULL COMMENT '详细地址',
  `industry_id` int NULL COMMENT '所属行业',
  `level` int NULL COMMENT '客户等级',
  `source` int NULL COMMENT '客户来源',
  `remark` varchar(1024) NULL COMMENT '备注',
  `country_code` varchar(255) NULL COMMENT '国家/地区，ISO 3166-1 二字码，例如 US、DE',
  `company_name` varchar(255) NULL COMMENT '公司名称',
  `website` varchar(255) NULL COMMENT '公司官网',
  `whatsapp` varchar(255) NULL COMMENT 'WhatsApp 号码，含国际区号',
  `group_id` bigint NULL COMMENT '客户分组编号，关联 crm_customer_group',
  `tag_ids` varchar(2000) NULL COMMENT '标签编号列表，关联 crm_tag',
  `ai_hosted` bit(1) NULL DEFAULT b'0' COMMENT '是否由 AI 数字员工托管接待',
  `starred` bit(1) NULL DEFAULT b'0' COMMENT '是否特别关注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 客户 DO';

DROP TABLE IF EXISTS `crm_customer_limit_config`;
CREATE TABLE `crm_customer_limit_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `type` int NULL COMMENT '规则类型',
  `user_ids` varchar(2000) NULL COMMENT '规则适用人群',
  `dept_ids` varchar(2000) NULL COMMENT '规则适用部门',
  `max_count` int NULL COMMENT '数量上限',
  `deal_count_enabled` bit(1) NULL DEFAULT b'0' COMMENT '成交客户是否占有拥有客户数',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户限制配置 DO';

DROP TABLE IF EXISTS `crm_customer_pool_config`;
CREATE TABLE `crm_customer_pool_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否启用客户公海',
  `contact_expire_days` int NULL COMMENT '未跟进放入公海天数',
  `deal_expire_days` int NULL COMMENT '未成交放入公海天数',
  `notify_enabled` bit(1) NULL DEFAULT b'0' COMMENT '是否开启提前提醒',
  `notify_days` int NULL COMMENT '提前提醒天数',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '客户公海配置 DO';

DROP TABLE IF EXISTS `crm_follow_up_record`;
CREATE TABLE `crm_follow_up_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int NULL COMMENT '数据类型',
  `biz_id` bigint NULL COMMENT '数据编号',
  `type` int NULL COMMENT '跟进类型',
  `content` mediumtext NULL COMMENT '跟进内容',
  `next_time` datetime NULL COMMENT '下次联系时间',
  `pic_urls` varchar(2000) NULL COMMENT '图片',
  `file_urls` varchar(2000) NULL COMMENT '附件',
  `business_ids` varchar(2000) NULL COMMENT '关联的商机编号数组',
  `contact_ids` varchar(2000) NULL COMMENT '关联的联系人编号数组',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '跟进记录 DO';

DROP TABLE IF EXISTS `crm_customer_group`;
CREATE TABLE `crm_customer_group` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '名称',
  `parent_id` bigint NULL COMMENT '父级编号',
  `sort` int NULL COMMENT '排序，越小越靠前',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 客户分组 DO，树形结构，parentId = 0 表示顶级';

DROP TABLE IF EXISTS `crm_performance_config`;
CREATE TABLE `crm_performance_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int NULL COMMENT '目标类型',
  `object_id` bigint NULL COMMENT '目标对象编号',
  `object_type` int NULL COMMENT '目标对象类型',
  `year` int NULL COMMENT '年份',
  `year_target_price` decimal(24, 6) NULL COMMENT '年度目标金额',
  `january_target_price` decimal(24, 6) NULL COMMENT '一月目标金额',
  `february_target_price` decimal(24, 6) NULL COMMENT '二月目标金额',
  `march_target_price` decimal(24, 6) NULL COMMENT '三月目标金额',
  `april_target_price` decimal(24, 6) NULL COMMENT '四月目标金额',
  `may_target_price` decimal(24, 6) NULL COMMENT '五月目标金额',
  `june_target_price` decimal(24, 6) NULL COMMENT '六月目标金额',
  `july_target_price` decimal(24, 6) NULL COMMENT '七月目标金额',
  `august_target_price` decimal(24, 6) NULL COMMENT '八月目标金额',
  `september_target_price` decimal(24, 6) NULL COMMENT '九月目标金额',
  `october_target_price` decimal(24, 6) NULL COMMENT '十月目标金额',
  `november_target_price` decimal(24, 6) NULL COMMENT '十一月目标金额',
  `december_target_price` decimal(24, 6) NULL COMMENT '十二月目标金额',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 业绩目标 DO';

DROP TABLE IF EXISTS `crm_owner_record`;
CREATE TABLE `crm_owner_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int NULL COMMENT 'CRM 业务类型',
  `biz_id` bigint NULL COMMENT 'CRM 业务编号',
  `pre_owner_user_id` bigint NULL COMMENT '变更前负责人',
  `post_owner_user_id` bigint NULL COMMENT '变更后负责人',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 负责人变更记录 DO';

DROP TABLE IF EXISTS `crm_permission`;
CREATE TABLE `crm_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `biz_type` int NULL COMMENT '数据类型',
  `biz_id` bigint NULL COMMENT '数据编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `level` int NULL COMMENT '权限级别',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 数据权限 DO';

DROP TABLE IF EXISTS `crm_product_category`;
CREATE TABLE `crm_product_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '分类名称',
  `parent_id` bigint NULL COMMENT '父级编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '产品分类 DO';

DROP TABLE IF EXISTS `crm_product`;
CREATE TABLE `crm_product` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '产品名称',
  `no` varchar(255) NULL COMMENT '产品编码',
  `unit` int NULL COMMENT '单位',
  `price` decimal(24, 6) NULL COMMENT '价格，单位：元',
  `status` int NULL COMMENT '状态',
  `category_id` bigint NULL COMMENT '产品分类 ID',
  `description` varchar(1024) NULL COMMENT '产品描述',
  `owner_user_id` bigint NULL COMMENT '负责人的用户编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 产品 DO';

DROP TABLE IF EXISTS `crm_receivable`;
CREATE TABLE `crm_receivable` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(255) NULL COMMENT '回款编号',
  `plan_id` bigint NULL COMMENT '回款计划编号',
  `customer_id` bigint NULL COMMENT '客户编号',
  `contract_id` bigint NULL COMMENT '合同编号',
  `owner_user_id` bigint NULL COMMENT '负责人编号，关联 {@link AdminUserRespDTO#getId()}',
  `return_time` datetime NULL COMMENT '回款日期',
  `return_type` int NULL COMMENT '回款方式',
  `price` decimal(24, 6) NULL COMMENT '计划回款金额，单位：元',
  `remark` varchar(1024) NULL COMMENT '备注',
  `process_instance_id` varchar(255) NULL COMMENT '工作流编号',
  `audit_status` int NULL COMMENT '审批状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '回款 DO';

DROP TABLE IF EXISTS `crm_receivable_plan`;
CREATE TABLE `crm_receivable_plan` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `period` int NULL COMMENT '期数',
  `customer_id` bigint NULL COMMENT '客户编号',
  `contract_id` bigint NULL COMMENT '合同编号',
  `owner_user_id` bigint NULL COMMENT '负责人编号',
  `return_time` datetime NULL COMMENT '计划回款日期',
  `return_type` int NULL COMMENT '计划回款类型',
  `price` decimal(24, 6) NULL COMMENT '计划回款金额，单位：元',
  `receivable_id` bigint NULL COMMENT '回款编号，关联 {@link CrmReceivableDO#getId()}',
  `remind_days` int NULL COMMENT '提前几天提醒',
  `remind_time` datetime NULL COMMENT '提醒日期',
  `remark` varchar(1024) NULL COMMENT '备注',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 回款计划 DO';

DROP TABLE IF EXISTS `crm_tag`;
CREATE TABLE `crm_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '名称',
  `parent_id` bigint NULL COMMENT '父级编号',
  `sort` int NULL COMMENT '排序，越小越靠前',
  `color` varchar(255) NULL COMMENT '显示颜色，例如 #1677ff',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'CRM 标签 DO，树形结构，parentId = 0 表示顶级';
