-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成
SET NAMES utf8mb4;

DROP TABLE IF EXISTS `pay_app`;
CREATE TABLE `pay_app` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `app_key` varchar(255) NULL COMMENT '应用标识',
  `name` varchar(255) NULL COMMENT '应用名',
  `status` int NULL COMMENT '状态',
  `remark` varchar(1024) NULL COMMENT '备注',
  `order_notify_url` varchar(255) NULL COMMENT '支付结果的回调地址',
  `refund_notify_url` varchar(255) NULL COMMENT '退款结果的回调地址',
  `transfer_notify_url` varchar(255) NULL COMMENT '转账结果的回调地址',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付应用 DO';

DROP TABLE IF EXISTS `pay_channel`;
CREATE TABLE `pay_channel` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `code` varchar(255) NULL COMMENT '渠道编码',
  `status` int NULL COMMENT '状态',
  `fee_rate` varchar(255) NULL COMMENT '渠道费率，单位：百分比',
  `remark` varchar(1024) NULL COMMENT '备注',
  `app_id` bigint NULL COMMENT '应用编号',
  `config` varchar(255) NULL COMMENT '支付渠道配置',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付渠道 DO';

DROP TABLE IF EXISTS `pay_demo_order`;
CREATE TABLE `pay_demo_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `spu_id` bigint NULL COMMENT '商品编号',
  `spu_name` varchar(255) NULL COMMENT '商品名称',
  `price` int NULL COMMENT '价格，单位：分',
  `pay_status` bit(1) NULL DEFAULT b'0' COMMENT '是否支付',
  `pay_order_id` bigint NULL COMMENT '支付订单编号',
  `pay_time` datetime NULL COMMENT '付款时间',
  `pay_channel_code` varchar(255) NULL COMMENT '支付渠道',
  `pay_refund_id` bigint NULL COMMENT '支付退款单号',
  `refund_price` int NULL COMMENT '退款金额，单位：分',
  `refund_time` datetime NULL COMMENT '退款完成时间',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '示例订单';

DROP TABLE IF EXISTS `pay_demo_withdraw`;
CREATE TABLE `pay_demo_withdraw` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `subject` varchar(255) NULL COMMENT '提现标题',
  `price` int NULL COMMENT '提现金额，单位：分',
  `user_account` varchar(255) NULL COMMENT '收款人账号',
  `user_name` varchar(255) NULL COMMENT '收款人姓名',
  `type` int NULL COMMENT '提现方式',
  `status` int NULL COMMENT '提现状态',
  `pay_transfer_id` bigint NULL COMMENT '转账单编号',
  `transfer_channel_code` varchar(255) NULL COMMENT '转账渠道',
  `transfer_time` datetime NULL COMMENT '转账成功时间',
  `transfer_error_msg` varchar(255) NULL COMMENT '转账错误提示',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '示例提现订单';

DROP TABLE IF EXISTS `pay_notify_log`;
CREATE TABLE `pay_notify_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `task_id` bigint NULL COMMENT '通知任务编号',
  `notify_times` int NULL COMMENT '第几次被通知',
  `response` varchar(255) NULL COMMENT 'HTTP 响应结果',
  `status` int NULL COMMENT '支付通知状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '商户支付、退款等的通知 Log';

DROP TABLE IF EXISTS `pay_notify_task`;
CREATE TABLE `pay_notify_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `app_id` bigint NULL COMMENT '应用编号',
  `type` int NULL COMMENT '通知类型',
  `data_id` bigint NULL COMMENT '数据编号，根据不同 type 进行关联：',
  `merchant_order_id` varchar(255) NULL COMMENT '商户订单编号',
  `merchant_refund_id` varchar(255) NULL COMMENT '商户退款编号',
  `merchant_transfer_id` varchar(255) NULL COMMENT '商户转账编号',
  `status` int NULL COMMENT '通知状态',
  `next_notify_time` datetime NULL COMMENT '下一次通知时间',
  `last_execute_time` datetime NULL COMMENT '最后一次执行时间',
  `notify_times` int NULL COMMENT '当前通知次数',
  `max_notify_times` int NULL COMMENT '最大可通知次数',
  `notify_url` varchar(255) NULL COMMENT '通知地址',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付通知';

DROP TABLE IF EXISTS `pay_order`;
CREATE TABLE `pay_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `app_id` bigint NULL COMMENT '应用编号',
  `channel_id` bigint NULL COMMENT '渠道编号',
  `channel_code` varchar(255) NULL COMMENT '渠道编码',
  `user_id` bigint NULL COMMENT '用户编号',
  `user_type` int NULL COMMENT '用户类型',
  `merchant_order_id` varchar(255) NULL COMMENT '商户订单编号',
  `subject` varchar(255) NULL COMMENT '商品标题',
  `body` varchar(255) NULL COMMENT '商品描述信息',
  `notify_url` varchar(255) NULL COMMENT '异步通知地址',
  `price` int NULL COMMENT '支付金额，单位：分',
  `channel_fee_rate` varchar(255) NULL COMMENT '渠道手续费，单位：百分比',
  `channel_fee_price` int NULL COMMENT '渠道手续金额，单位：分',
  `status` int NULL COMMENT '支付状态',
  `user_ip` varchar(255) NULL COMMENT '用户 IP',
  `expire_time` datetime NULL COMMENT '订单失效时间',
  `success_time` datetime NULL COMMENT '订单支付成功时间',
  `extension_id` bigint NULL COMMENT '支付成功的订单拓展单编号',
  `no` varchar(255) NULL COMMENT '支付成功的外部订单号',
  `refund_price` int NULL COMMENT '退款总金额，单位：分',
  `channel_user_id` varchar(255) NULL COMMENT '渠道用户编号',
  `channel_order_no` varchar(255) NULL COMMENT '渠道订单号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付订单 DO';

DROP TABLE IF EXISTS `pay_order_extension`;
CREATE TABLE `pay_order_extension` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(255) NULL COMMENT '外部订单号，根据规则生成',
  `order_id` bigint NULL COMMENT '订单号',
  `channel_id` bigint NULL COMMENT '渠道编号',
  `channel_code` varchar(255) NULL COMMENT '渠道编码',
  `user_ip` varchar(255) NULL COMMENT '用户 IP',
  `status` int NULL COMMENT '支付状态',
  `channel_extras` varchar(255) NULL COMMENT '支付渠道的额外参数',
  `channel_error_code` varchar(255) NULL COMMENT '调用渠道的错误码',
  `channel_error_msg` varchar(255) NULL COMMENT '调用渠道报错时，错误信息',
  `channel_notify_data` varchar(255) NULL COMMENT '支付渠道的同步/异步通知的内容',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付订单拓展 DO';

DROP TABLE IF EXISTS `pay_refund`;
CREATE TABLE `pay_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(255) NULL COMMENT '外部退款号，根据规则生成',
  `app_id` bigint NULL COMMENT '应用编号',
  `channel_id` bigint NULL COMMENT '渠道编号',
  `channel_code` varchar(255) NULL COMMENT '渠道编码',
  `order_id` bigint NULL COMMENT '订单编号',
  `order_no` varchar(255) NULL COMMENT '支付订单编号',
  `user_id` bigint NULL COMMENT '用户编号',
  `user_type` int NULL COMMENT '用户类型',
  `merchant_order_id` varchar(255) NULL COMMENT '商户订单编号',
  `merchant_refund_id` varchar(255) NULL COMMENT '商户退款订单号',
  `notify_url` varchar(255) NULL COMMENT '异步通知地址',
  `status` int NULL COMMENT '退款状态',
  `pay_price` int NULL COMMENT '支付金额，单位：分',
  `refund_price` int NULL COMMENT '退款金额，单位：分',
  `reason` varchar(255) NULL COMMENT '退款原因',
  `user_ip` varchar(255) NULL COMMENT '用户 IP',
  `channel_order_no` varchar(255) NULL COMMENT '渠道订单号',
  `channel_refund_no` varchar(255) NULL COMMENT '渠道退款单号',
  `success_time` datetime NULL COMMENT '退款成功时间',
  `channel_error_code` varchar(255) NULL COMMENT '调用渠道的错误码',
  `channel_error_msg` varchar(255) NULL COMMENT '调用渠道的错误提示',
  `channel_notify_data` varchar(255) NULL COMMENT '支付渠道的同步/异步通知的内容',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '支付退款单 DO';

DROP TABLE IF EXISTS `pay_transfer`;
CREATE TABLE `pay_transfer` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(255) NULL COMMENT '转账单号',
  `app_id` bigint NULL COMMENT '应用编号',
  `channel_id` bigint NULL COMMENT '转账渠道编号',
  `channel_code` varchar(255) NULL COMMENT '转账渠道编码',
  `user_id` bigint NULL COMMENT '用户编号',
  `user_type` int NULL COMMENT '用户类型',
  `merchant_transfer_id` varchar(255) NULL COMMENT '商户转账单编号',
  `subject` varchar(255) NULL COMMENT '转账标题',
  `price` int NULL COMMENT '转账金额，单位：分',
  `user_account` varchar(255) NULL COMMENT '收款人账号',
  `user_name` varchar(255) NULL COMMENT '收款人姓名',
  `status` int NULL COMMENT '转账状态',
  `success_time` datetime NULL COMMENT '订单转账成功时间',
  `notify_url` varchar(255) NULL COMMENT '异步通知地址',
  `user_ip` varchar(255) NULL COMMENT '用户 IP',
  `channel_extras` varchar(255) NULL COMMENT '渠道的额外参数',
  `channel_transfer_no` varchar(255) NULL COMMENT '渠道转账单号',
  `channel_error_code` varchar(255) NULL COMMENT '调用渠道的错误码',
  `channel_error_msg` varchar(255) NULL COMMENT '调用渠道的错误提示',
  `channel_notify_data` varchar(255) NULL COMMENT '渠道的同步/异步通知的内容',
  `channel_package_info` varchar(255) NULL COMMENT '渠道 package 信息',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '转账单 DO';

DROP TABLE IF EXISTS `pay_wallet`;
CREATE TABLE `pay_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NULL COMMENT '用户 id',
  `user_type` int NULL COMMENT '用户类型, 预留 多商户转帐可能需要用到',
  `balance` int NULL COMMENT '余额，单位分',
  `freeze_price` int NULL COMMENT '冻结金额，单位分',
  `total_expense` int NULL COMMENT '累计支出，单位分',
  `total_recharge` int NULL COMMENT '累计充值，单位分',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会员钱包 DO';

DROP TABLE IF EXISTS `pay_wallet_recharge`;
CREATE TABLE `pay_wallet_recharge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `wallet_id` bigint NULL COMMENT '钱包编号',
  `total_price` int NULL COMMENT '用户实际到账余额',
  `pay_price` int NULL COMMENT '实际支付金额',
  `bonus_price` int NULL COMMENT '钱包赠送金额',
  `package_id` bigint NULL COMMENT '充值套餐编号',
  `pay_status` bit(1) NULL DEFAULT b'0' COMMENT '是否已支付',
  `pay_order_id` bigint NULL COMMENT '支付订单编号',
  `pay_channel_code` varchar(255) NULL COMMENT '支付成功的支付渠道',
  `pay_time` datetime NULL COMMENT '订单支付时间',
  `pay_refund_id` bigint NULL COMMENT '支付退款单编号',
  `refund_total_price` int NULL COMMENT '退款金额，包含赠送金额',
  `refund_pay_price` int NULL COMMENT '退款支付金额',
  `refund_bonus_price` int NULL COMMENT '退款钱包赠送金额',
  `refund_time` datetime NULL COMMENT '退款时间',
  `refund_status` int NULL COMMENT '退款状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会员钱包充值';

DROP TABLE IF EXISTS `pay_wallet_recharge_package`;
CREATE TABLE `pay_wallet_recharge_package` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `name` varchar(255) NULL COMMENT '套餐名',
  `pay_price` int NULL COMMENT '支付金额',
  `bonus_price` int NULL COMMENT '赠送金额',
  `status` int NULL COMMENT '状态',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会员钱包充值套餐 DO';

DROP TABLE IF EXISTS `pay_wallet_transaction`;
CREATE TABLE `pay_wallet_transaction` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `no` varchar(255) NULL COMMENT '流水号',
  `wallet_id` bigint NULL COMMENT '钱包编号',
  `biz_type` int NULL COMMENT '关联业务分类',
  `biz_id` varchar(255) NULL COMMENT '关联业务编号',
  `title` varchar(255) NULL COMMENT '流水说明',
  `price` int NULL COMMENT '交易金额，单位分',
  `balance` int NULL COMMENT '交易后余额，单位分',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会员钱包流水 DO';
