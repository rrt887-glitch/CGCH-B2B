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


    // ========== WhatsApp 1-030-002-000 ==========
    ErrorCode WA_ACCOUNT_NOT_EXISTS = new ErrorCode(1_030_002_000, "WhatsApp 号码不存在");
    ErrorCode WA_ACCOUNT_EXISTS = new ErrorCode(1_030_002_001, "该 Phone number ID 已被绑定");
    ErrorCode WA_API_FAIL = new ErrorCode(1_030_002_002, "WhatsApp 接口调用失败：{}");
    ErrorCode WA_CONVERSATION_NOT_EXISTS = new ErrorCode(1_030_002_003, "会话不存在");
    ErrorCode WA_WINDOW_CLOSED = new ErrorCode(1_030_002_004, "客户超过 24 小时未回复，只能发送已审核的模板消息");
    ErrorCode WA_CONTENT_EMPTY = new ErrorCode(1_030_002_005, "请填写消息内容或选择模板");
    ErrorCode WA_TOKEN_REQUIRED = new ErrorCode(1_030_002_006, "请填写访问令牌与应用密钥");
    ErrorCode WA_PHONE_INVALID = new ErrorCode(1_030_002_007, "客户号码格式不正确，需包含国家码");

}
