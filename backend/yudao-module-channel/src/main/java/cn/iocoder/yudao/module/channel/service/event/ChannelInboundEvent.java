package cn.iocoder.yudao.module.channel.service.event;

/**
 * 收到客户新消息。邮件：refId 为邮件编号；WhatsApp：refId 为会话编号、messageId 为本条消息编号。
 */
public record ChannelInboundEvent(String channel, Long refId, Long messageId, Long tenantId) {
}
