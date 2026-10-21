package cn.iocoder.yudao.module.channel.service.notify;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateItemDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateTaskDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.reactivate.ChannelReactivateItemMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.reactivate.ChannelReactivateTaskMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.yudao.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import cn.iocoder.yudao.module.system.dal.dataobject.tenant.TenantDO;
import cn.iocoder.yudao.module.system.service.tenant.TenantService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通知中心：把需要人处理的事以站内信发给对应的业务员（顶部铃铛和「通知中心」页面可见）。
 * 通知失败只记日志，不影响主流程。
 */
@Service
@Slf4j
public class ChannelNotifyService {

    public static final String TYPE_HANDOFF = "handoff";
    public static final String TYPE_TAKEOVER = "takeover";
    public static final String TYPE_NEW_LEAD = "new_lead";
    public static final String TYPE_REACTIVATE_REPLY = "reactivate_reply";
    public static final String TYPE_CREDIT_LOW = "credit_low";
    /** 站内信模板编码前缀，对应 03-channel-seed.sql 中的模板 */
    public static final String TEMPLATE_PREFIX = "cgch_";
    /** 同一对象同类通知的最短间隔，避免客户连发消息时刷屏 */
    private static final long DEDUP_MS = 30 * 60 * 1000L;
    private static final Map<String, String> REASON = Map.of(
            ChannelAiDraftDO.REASON_HANDOFF, "AI 判断需要人工处理（投诉、退款、合同等）",
            ChannelAiDraftDO.REASON_FORBIDDEN, "AI 回复中含有违禁词，已拦截",
            ChannelAiDraftDO.REASON_SEND_FAILED, "AI 回复自动发送失败");

    private final Map<String, Long> recent = new ConcurrentHashMap<>();

    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;
    @Resource
    private TenantService tenantService;
    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelReactivateItemMapper channelReactivateItemMapper;
    @Resource
    private ChannelReactivateTaskMapper channelReactivateTaskMapper;

    @Value("${cgch.credit.low-threshold:100}")
    private long lowThreshold;

    // ==================== 发送 ====================

    private boolean muted(String type) {
        ChannelAiConfigDO c = channelAiConfigMapper.selectCurrent();
        return c != null && StrUtil.split(StrUtil.nullToEmpty(c.getNotifyMuted()), ',', true, true).contains(type);
    }

    /** @param dedupKey 非空时同一对象 30 分钟内只通知一次 */
    void send(String type, Long userId, Map<String, Object> params, String link, String dedupKey) {
        if (userId == null || muted(type)) {
            return;
        }
        if (dedupKey != null) {
            String k = TenantContextHolder.getTenantId() + ":" + type + ":" + userId + ":" + dedupKey;
            long now = System.currentTimeMillis();
            Long last = recent.get(k);
            if (last != null && now - last < DEDUP_MS) {
                return;
            }
            recent.put(k, now);
            if (recent.size() > 5000) {
                recent.entrySet().removeIf(e -> now - e.getValue() > DEDUP_MS);
            }
        }
        Map<String, Object> p = new LinkedHashMap<>(params);
        p.put("type", type);
        p.put("link", StrUtil.nullToEmpty(link));
        try {
            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                    .setUserId(userId).setTemplateCode(TEMPLATE_PREFIX + type).setTemplateParams(p));
        } catch (Exception e) {
            log.warn("[send][通知 {} 给用户 {} 失败] {}", type, userId, e.getMessage());
        }
    }

    // ==================== 各类事件 ====================

    /** 对象（邮件 / WhatsApp 会话）的客户显示名、所属业务员、跳转链接 */
    private record Target(String customer, Long ownerUserId, String link) {
    }

    private Target target(String channel, Long refId) {
        if (ChannelAiDraftDO.CHANNEL_MAIL.equals(channel)) {
            ChannelMailMessageDO m = channelMailMessageMapper.selectById(refId);
            if (m == null) {
                return null;
            }
            ChannelMailAccountDO a = channelMailAccountMapper.selectById(m.getAccountId());
            return new Target(StrUtil.blankToDefault(m.getFromName(), m.getFromAddress()), a == null ? null : a.getUserId(),
                    "/channel-msg/mail?id=" + m.getId());
        }
        ChannelWaConversationDO c = channelWaConversationMapper.selectById(refId);
        if (c == null) {
            return null;
        }
        ChannelWaAccountDO a = channelWaAccountMapper.selectById(c.getAccountId());
        return new Target(StrUtil.blankToDefault(c.getProfileName(), "+" + c.getWaId()), a == null ? null : a.getUserId(),
                "/channel-msg/im?conversationId=" + c.getId());
    }

    /** AI 留下需要人工处理的草稿：转人工、违禁词、发送失败 */
    public void onDraft(ChannelAiDraftDO d) {
        if (!ChannelAiDraftDO.STATUS_PENDING.equals(d.getStatus()) || !REASON.containsKey(d.getReason())) {
            return;
        }
        Target t = target(d.getChannel(), d.getRefId());
        if (t != null) {
            send(TYPE_HANDOFF, t.ownerUserId(), Map.of("customer", t.customer(), "reason", REASON.get(d.getReason())),
                    t.link(), d.getChannel() + ":" + d.getRefId());
        }
    }

    /** 业务员优先等待超时，AI 已接手 */
    public void onTakeover(String channel, Long refId, int minutes) {
        Target t = target(channel, refId);
        if (t != null) {
            send(TYPE_TAKEOVER, t.ownerUserId(), Map.of("customer", t.customer(), "minutes", minutes), t.link(), channel + ":" + refId);
        }
    }

    /** 新询盘自动建了线索 */
    public void onNewLead(Long clueId, String name, Long ownerUserId, String source) {
        send(TYPE_NEW_LEAD, ownerUserId, Map.of("name", StrUtil.blankToDefault(name, "新客户"), "source", source),
                "/crm/clue/detail/" + clueId, null);
    }

    /** 收到消息时检查：是否为客户唤醒的回复 */
    public void onInbound(String channel, Long refId, Long messageId) {
        List<ChannelReactivateItemDO> items;
        if ("mail".equals(channel)) {
            ChannelMailMessageDO m = channelMailMessageMapper.selectById(messageId);
            if (m == null || m.getCustomerId() == null) {
                return;
            }
            items = channelReactivateItemMapper.selectList(new LambdaQueryWrapperX<ChannelReactivateItemDO>()
                    .eq(ChannelReactivateItemDO::getCustomerId, m.getCustomerId()).eq(ChannelReactivateItemDO::getChannel, "mail")
                    .eq(ChannelReactivateItemDO::getStatus, ChannelReactivateItemDO.STATUS_SENT)
                    .lt(ChannelReactivateItemDO::getSentRefId, m.getId())
                    .and(w -> w.isNull(ChannelReactivateItemDO::getReplyNotified).or().eq(ChannelReactivateItemDO::getReplyNotified, false)));
        } else {
            ChannelWaMessageDO wm = channelWaMessageMapper.selectById(messageId);
            if (wm == null) {
                return;
            }
            List<Long> sentIds = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .eq(ChannelWaMessageDO::getConversationId, wm.getConversationId())
                    .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_OUT).lt(ChannelWaMessageDO::getId, wm.getId())
                    .orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 50")).stream().map(ChannelWaMessageDO::getId).toList();
            if (sentIds.isEmpty()) {
                return;
            }
            items = channelReactivateItemMapper.selectList(new LambdaQueryWrapperX<ChannelReactivateItemDO>()
                    .in(ChannelReactivateItemDO::getSentRefId, sentIds).eq(ChannelReactivateItemDO::getChannel, "whatsapp")
                    .eq(ChannelReactivateItemDO::getStatus, ChannelReactivateItemDO.STATUS_SENT)
                    .and(w -> w.isNull(ChannelReactivateItemDO::getReplyNotified).or().eq(ChannelReactivateItemDO::getReplyNotified, false)));
        }
        for (ChannelReactivateItemDO i : items) {
            ChannelReactivateTaskDO task = channelReactivateTaskMapper.selectById(i.getTaskId());
            channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(i.getId()).setReplyNotified(true));
            Target t = target(channel, "mail".equals(channel) ? messageId : refId);
            send(TYPE_REACTIVATE_REPLY, i.getOwnerUserId(), Map.of("customer", i.getCustomerName(),
                    "task", task == null ? "客户唤醒" : task.getName()), t == null ? "/claw/reactivate" : t.link(), null);
        }
    }

    /** 智点余额跌破提醒线或用完时，通知租户联系人（未设置联系人时通知本次消耗的业务员） */
    public void onBalanceChanged(long before, long after, Long actorUserId) {
        boolean crossedLow = before >= lowThreshold && after < lowThreshold && after > 0;
        boolean usedUp = before > 0 && after <= 0;
        if (!crossedLow && !usedUp) {
            return;
        }
        Long tenantId = TenantContextHolder.getTenantId();
        TenantDO tenant = tenantId == null ? null : tenantService.getTenant(tenantId);
        Long to = tenant != null && tenant.getContactUserId() != null ? tenant.getContactUserId() : actorUserId;
        if (to != null) {
            send(TYPE_CREDIT_LOW, to, Map.of("balance", Math.max(after, 0),
                    "hint", usedUp ? "AI 已停止起草和自动回复" : "用完后 AI 将停止回复"), "/config/credits", null);
        }
    }

}
