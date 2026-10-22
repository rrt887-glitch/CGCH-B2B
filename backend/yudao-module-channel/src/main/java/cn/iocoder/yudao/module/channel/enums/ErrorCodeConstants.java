package cn.iocoder.yudao.module.channel.enums;

import cn.iocoder.yudao.framework.common.exception.ErrorCode;

/**
 * 渠道模块错误码，区间 1-030-000-000 ~ 1-030-999-999
 */
public interface ErrorCodeConstants {

    // ========== 邮箱账号 1-030-000-000 ==========
    ErrorCode MAIL_ACCOUNT_NOT_EXISTS = new ErrorCode(1_030_000_000, "邮箱账号不存在");
    ErrorCode MAIL_ACCOUNT_EMAIL_EXISTS = new ErrorCode(1_030_000_001, "该邮箱已被绑定");
    ErrorCode MAIL_ACCOUNT_CONNECT_FAIL = new ErrorCode(1_030_000_002, "邮箱连接失败：{}");
    ErrorCode MAIL_ACCOUNT_SYNC_FAIL = new ErrorCode(1_030_000_003, "收信失败：{}");

    // ========== 邮件 1-030-001-000 ==========
    ErrorCode MAIL_MESSAGE_NOT_EXISTS = new ErrorCode(1_030_001_000, "邮件不存在");
    ErrorCode MAIL_SEND_FAIL = new ErrorCode(1_030_001_001, "发送失败：{}");
    ErrorCode MAIL_RECIPIENT_EMPTY = new ErrorCode(1_030_001_002, "收件人不能为空");
    ErrorCode MAIL_ATTACHMENT_EMPTY = new ErrorCode(1_030_001_003, "请选择要上传的文件");
    ErrorCode MAIL_ATTACHMENT_TOO_LARGE = new ErrorCode(1_030_001_004, "单个附件不能超过 25MB");
    ErrorCode MAIL_ATTACHMENT_NOT_EXISTS = new ErrorCode(1_030_001_005, "附件不存在或已被使用");


    // ========== WhatsApp 1-030-002-000 ==========
    ErrorCode WA_ACCOUNT_NOT_EXISTS = new ErrorCode(1_030_002_000, "WhatsApp 号码不存在");
    ErrorCode WA_ACCOUNT_EXISTS = new ErrorCode(1_030_002_001, "该 Phone number ID 已被绑定");
    ErrorCode WA_API_FAIL = new ErrorCode(1_030_002_002, "WhatsApp 接口调用失败：{}");
    ErrorCode WA_CONVERSATION_NOT_EXISTS = new ErrorCode(1_030_002_003, "会话不存在");
    ErrorCode WA_WINDOW_CLOSED = new ErrorCode(1_030_002_004, "客户超过 24 小时未回复，只能发送已审核的模板消息");
    ErrorCode WA_CONTENT_EMPTY = new ErrorCode(1_030_002_005, "请填写消息内容或选择模板");
    ErrorCode WA_TOKEN_REQUIRED = new ErrorCode(1_030_002_006, "请填写访问令牌与应用密钥");
    ErrorCode WA_PHONE_INVALID = new ErrorCode(1_030_002_007, "客户号码格式不正确，需包含国家码");
    ErrorCode WA_MESSAGE_NOT_EXISTS = new ErrorCode(1_030_002_008, "消息不存在");
    ErrorCode WA_NO_MEDIA = new ErrorCode(1_030_002_009, "该消息没有媒体文件");
    ErrorCode WA_FILE_TOO_LARGE = new ErrorCode(1_030_002_010, "文件过大：图片不超过 5MB，其他文件不超过 16MB");
    ErrorCode WA_WABA_REQUIRED = new ErrorCode(1_030_002_011, "请填写 WhatsApp Business Account ID");
    ErrorCode META_PLATFORM_INVALID = new ErrorCode(1_030_002_012, "不支持的平台：{}");
    ErrorCode META_TEMPLATE_UNSUPPORTED = new ErrorCode(1_030_002_013, "{} 不支持模板消息，只能在客户来信后 24 小时内回复");
    ErrorCode META_NEW_CONVERSATION_UNSUPPORTED = new ErrorCode(1_030_002_014, "{} 不能主动发起会话，请等待客户先发消息");
    ErrorCode META_MEDIA_UNSUPPORTED = new ErrorCode(1_030_002_015, "Instagram 私信暂不支持从系统发送文件，请发送文字");
    ErrorCode META_OAUTH_FAIL = new ErrorCode(1_030_002_016, "Facebook 授权失败：{}");


    // ========== AI 数字员工 1-030-003-000 ==========
    ErrorCode AI_LLM_FAIL = new ErrorCode(1_030_003_000, "{}");
    ErrorCode AI_KNOWLEDGE_EMPTY = new ErrorCode(1_030_003_001, "知识内容不能为空");
    ErrorCode AI_KNOWLEDGE_NOT_EXISTS = new ErrorCode(1_030_003_002, "知识条目不存在");
    ErrorCode AI_FORBIDDEN_EMPTY = new ErrorCode(1_030_003_003, "违禁词不能为空");
    ErrorCode AI_FORBIDDEN_EXISTS = new ErrorCode(1_030_003_004, "该违禁词已存在");
    ErrorCode AI_DRAFT_NOT_EXISTS = new ErrorCode(1_030_003_005, "草稿不存在");
    ErrorCode AI_CREDIT_INSUFFICIENT = new ErrorCode(1_030_003_006, "智点余额不足，请联系管理员充值");
    ErrorCode AI_REPORT_NOT_EXISTS = new ErrorCode(1_030_003_008, "工作报告不存在");
    ErrorCode AI_EMBEDDING_NOT_CONFIGURED = new ErrorCode(1_030_003_007, "请先在数字员工档案中填写向量模型");

    // ========== 智点 1-030-004-000 ==========
    ErrorCode CREDIT_AMOUNT_ZERO = new ErrorCode(1_030_004_000, "数量不能为 0");
    ErrorCode LEAD_RULE_INVALID = new ErrorCode(1_030_012_000, "{}");
    ErrorCode MAIL_OAUTH_FAIL = new ErrorCode(1_030_000_010, "邮箱授权失败：{}");
    ErrorCode SEND_POLICY_INVALID = new ErrorCode(1_030_011_000, "{}");
    ErrorCode SEND_SUPPRESSED = new ErrorCode(1_030_011_001, "收件人 {} 已退订或在黑名单中，不能发送");
    ErrorCode SEND_RATE_LIMITED = new ErrorCode(1_030_011_002, "{}");
    ErrorCode CASE_INVALID = new ErrorCode(1_030_010_000, "{}");
    ErrorCode CASE_NOT_EXISTS = new ErrorCode(1_030_010_001, "成交案例不存在");
    ErrorCode ASSISTANT_INVALID = new ErrorCode(1_030_009_000, "{}");
    ErrorCode ASSISTANT_CHAT_NOT_EXISTS = new ErrorCode(1_030_009_001, "会话不存在");
    ErrorCode RESEARCH_INVALID = new ErrorCode(1_030_008_000, "{}");
    ErrorCode RESEARCH_NOT_EXISTS = new ErrorCode(1_030_008_001, "背调记录不存在");
    ErrorCode REACTIVATE_NO_CUSTOMER = new ErrorCode(1_030_007_000, "请至少选择一位客户");
    ErrorCode REACTIVATE_TOO_MANY = new ErrorCode(1_030_007_001, "一次最多选择 {} 位客户");
    ErrorCode REACTIVATE_TASK_NOT_EXISTS = new ErrorCode(1_030_007_002, "唤醒任务不存在");
    ErrorCode REACTIVATE_ITEM_NOT_EXISTS = new ErrorCode(1_030_007_003, "唤醒内容不存在");
    ErrorCode REACTIVATE_ITEM_STATUS = new ErrorCode(1_030_007_004, "{}");
    ErrorCode WORK_TIME_INVALID = new ErrorCode(1_030_006_000, "{}");
    ErrorCode DASHBOARD_USER_FORBIDDEN = new ErrorCode(1_030_005_000, "无权查看该业务员的数据");
    ErrorCode CREDIT_PACKAGE_NOT_FOUND = new ErrorCode(1_030_004_001, "充值套餐不存在");
    ErrorCode CREDIT_ORDER_NOT_FOUND = new ErrorCode(1_030_004_002, "充值订单不存在");
    ErrorCode CREDIT_ORDER_PAY_MISMATCH = new ErrorCode(1_030_004_003, "支付单与充值订单不匹配");
    ErrorCode CREDIT_ORDER_NOT_PAID = new ErrorCode(1_030_004_004, "支付单尚未支付成功");
    ErrorCode CREDIT_ORDER_PRICE_MISMATCH = new ErrorCode(1_030_004_005, "支付金额与订单金额不一致");

}
