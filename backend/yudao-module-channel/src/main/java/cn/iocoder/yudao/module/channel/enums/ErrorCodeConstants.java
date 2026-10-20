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


    // ========== AI 数字员工 1-030-003-000 ==========
    ErrorCode AI_LLM_FAIL = new ErrorCode(1_030_003_000, "{}");
    ErrorCode AI_KNOWLEDGE_EMPTY = new ErrorCode(1_030_003_001, "知识内容不能为空");
    ErrorCode AI_KNOWLEDGE_NOT_EXISTS = new ErrorCode(1_030_003_002, "知识条目不存在");
    ErrorCode AI_FORBIDDEN_EMPTY = new ErrorCode(1_030_003_003, "违禁词不能为空");
    ErrorCode AI_FORBIDDEN_EXISTS = new ErrorCode(1_030_003_004, "该违禁词已存在");
    ErrorCode AI_DRAFT_NOT_EXISTS = new ErrorCode(1_030_003_005, "草稿不存在");
    ErrorCode AI_CREDIT_INSUFFICIENT = new ErrorCode(1_030_003_006, "智点余额不足，请联系管理员充值");

    // ========== 智点 1-030-004-000 ==========
    ErrorCode CREDIT_AMOUNT_ZERO = new ErrorCode(1_030_004_000, "数量不能为 0");
    ErrorCode CREDIT_PACKAGE_NOT_FOUND = new ErrorCode(1_030_004_001, "充值套餐不存在");
    ErrorCode CREDIT_ORDER_NOT_FOUND = new ErrorCode(1_030_004_002, "充值订单不存在");
    ErrorCode CREDIT_ORDER_PAY_MISMATCH = new ErrorCode(1_030_004_003, "支付单与充值订单不匹配");
    ErrorCode CREDIT_ORDER_NOT_PAID = new ErrorCode(1_030_004_004, "支付单尚未支付成功");
    ErrorCode CREDIT_ORDER_PRICE_MISMATCH = new ErrorCode(1_030_004_005, "支付金额与订单金额不一致");

}
