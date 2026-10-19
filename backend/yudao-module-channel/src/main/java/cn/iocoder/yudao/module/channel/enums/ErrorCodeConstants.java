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

}
