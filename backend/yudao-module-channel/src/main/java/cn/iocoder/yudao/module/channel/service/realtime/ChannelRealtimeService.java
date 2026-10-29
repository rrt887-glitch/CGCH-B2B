package cn.iocoder.yudao.module.channel.service.realtime;

import cn.iocoder.yudao.framework.common.enums.UserTypeEnum;
import cn.iocoder.yudao.framework.websocket.core.sender.WebSocketMessageSender;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实时推送：新消息、我方发出的消息（含 AI）、发送状态变化时，通过 WebSocket（/infra/ws）通知会话的处理人，
 * 前端收到后刷新会话列表和当前会话，不用等定时刷新。推送失败不影响收发。
 */
@Service
@Slf4j
public class ChannelRealtimeService {

    public static final String MESSAGE_TYPE = "channel-message";

    @Resource
    private ObjectProvider<WebSocketMessageSender> senderProvider;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;

    @EventListener
    public void onInbound(ChannelInboundEvent e) {
        if ("mail".equals(e.channel())) {
            mailChanged(e.refId(), "in");
        } else {
            conversationChanged(e.refId(), e.messageId(), "in");
        }
    }

    /** 即时消息会话有变化：推给处理人（转交后是负责人，否则是号码所属业务员） */
    public void conversationChanged(Long conversationId, Long messageId, String kind) {
        try {
            ChannelWaConversationDO c = conversationId == null ? null : channelWaConversationMapper.selectById(conversationId);
            if (c == null) {
                return;
            }
            Long userId = c.handlerUserId(channelWaAccountMapper.selectById(c.getAccountId()));
            push(userId, "whatsapp", conversationId, messageId, kind);
        } catch (Exception ex) {
            log.warn("[conversationChanged][会话 {} 推送失败] {}", conversationId, ex.getMessage());
        }
    }

    /** 邮件有变化：推给邮箱所属业务员 */
    public void mailChanged(Long mailMessageId, String kind) {
        try {
            ChannelMailMessageDO m = mailMessageId == null ? null : channelMailMessageMapper.selectById(mailMessageId);
            ChannelMailAccountDO a = m == null ? null : channelMailAccountMapper.selectById(m.getAccountId());
            if (a != null) {
                push(a.getUserId(), "mail", mailMessageId, mailMessageId, kind);
            }
        } catch (Exception ex) {
            log.warn("[mailChanged][邮件 {} 推送失败] {}", mailMessageId, ex.getMessage());
        }
    }

    /** 在事务里调用时等提交后再推，避免前端收到通知去查时还查不到新消息 */
    private void push(Long userId, String channel, Long refId, Long messageId, String kind) {
        log.debug("[push][用户 {} {} {} {}，事务同步 {}]", userId, channel, refId, kind,
                org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive());
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            send(userId, channel, refId, messageId, kind);
                        }
                    });
        } else {
            send(userId, channel, refId, messageId, kind);
        }
    }

    private void send(Long userId, String channel, Long refId, Long messageId, String kind) {
        WebSocketMessageSender sender = senderProvider.getIfAvailable();
        if (sender == null || userId == null) {
            log.debug("[send][没有推送通道或处理人：sender={} userId={}]", sender, userId);
            return;
        }
        log.debug("[send][推送给用户 {}：{} {} {}]", userId, channel, refId, kind);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel", channel);
        body.put("refId", refId);
        body.put("messageId", messageId);
        body.put("kind", kind);
        try {
            sender.sendObject(UserTypeEnum.ADMIN.getValue(), userId, MESSAGE_TYPE, body);
        } catch (Exception ex) {
            log.warn("[send][用户 {} 推送失败] {}", userId, ex.getMessage());
        }
    }

}
