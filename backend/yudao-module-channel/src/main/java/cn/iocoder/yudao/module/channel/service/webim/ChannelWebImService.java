package cn.iocoder.yudao.module.channel.service.webim;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 官网 IM：客户网站上嵌入一段脚本，访客在右下角聊天窗口里发消息。
 * 会话和消息存进与 WhatsApp / Messenger 相同的表（平台 web），所以统一出现在「会话」工作台，
 * AI 自动回复、线索建档、消息分流都与其他渠道一致；业务员或 AI 发出的消息由访客的聊天窗口轮询取走。
 */
@Service
@Slf4j
public class ChannelWebImService {

    /** 访客编号：聊天窗口第一次打开时随机生成，存在访客浏览器里 */
    private static final String VISITOR_PATTERN = "[A-Za-z0-9_-]{8,64}";
    private static final int MAX_TEXT = 2000;
    /** 同一访客每分钟最多发送的消息数 */
    private static final int PER_MINUTE = 20;

    @Resource
    private cn.iocoder.yudao.module.channel.service.assign.ChannelAssignService channelAssignService;
    @Resource
    private ChannelWaAccountMapper accountMapper;
    @Resource
    private ChannelWaConversationMapper conversationMapper;
    @Resource
    private ChannelWaMessageMapper messageMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    // ==================== 后台：绑定网站 ====================

    public static String normalizeDomain(String raw) {
        String d = StrUtil.trim(StrUtil.nullToEmpty(raw)).toLowerCase(Locale.ROOT);
        d = d.replaceFirst("^[a-z]+://", "");
        d = StrUtil.subBefore(d, "/", false);
        d = StrUtil.subBefore(d, ":", false);
        d = StrUtil.removePrefix(d, "www.");
        return d;
    }

    public Long createSite(Long userId, String domain, String name) {
        String d = normalizeDomain(domain);
        if (!d.matches("([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}") && !"localhost".equals(d)) {
            throw exception(AI_LLM_FAIL, "请输入正确的网站域名，例如 www.aib2b.com");
        }
        if (StrUtil.isBlank(name)) {
            throw exception(AI_LLM_FAIL, "请输入官网名称");
        }
        ChannelWaAccountDO a = ChannelWaAccountDO.builder().userId(userId).platform(ChannelWaAccountDO.PLATFORM_WEB)
                .authType(ChannelWaAccountDO.AUTH_MANUAL).name(StrUtil.maxLength(name.trim(), 60)).displayPhone(d)
                .phoneNumberId("web_" + IdUtil.fastSimpleUUID()).verifyToken("-").enabled(true).build();
        accountMapper.insert(a);
        return a.getId();
    }

    public void updateSite(Long userId, Long id, String domain, String name) {
        ChannelWaAccountDO a = accountMapper.selectById(id);
        if (a == null || !a.isWeb() || !Objects.equals(a.getUserId(), userId)) {
            throw exception(AI_LLM_FAIL, "网站不存在");
        }
        String d = normalizeDomain(domain);
        if (!d.matches("([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}") && !"localhost".equals(d)) {
            throw exception(AI_LLM_FAIL, "请输入正确的网站域名，例如 www.aib2b.com");
        }
        accountMapper.updateById(new ChannelWaAccountDO().setId(id).setDisplayPhone(d)
                .setName(StrUtil.maxLength(StrUtil.blankToDefault(StrUtil.trim(name), a.getName()), 60)));
    }

    // ==================== 访客：聊天窗口调用（无需登录） ====================

    public record SiteConfig(String name, String domain) {
    }

    public record VisitorMessage(Long id, String from, String type, String text, String fileName, String fileUrl, LocalDateTime time) {
    }

    private ChannelWaAccountDO site(String key, String origin) {
        if (StrUtil.isBlank(key) || key.length() > 64) {
            throw exception(AI_LLM_FAIL, "站点不存在");
        }
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(key));
        if (a == null || !a.isWeb() || !Boolean.TRUE.equals(a.getEnabled())) {
            throw exception(AI_LLM_FAIL, "站点不存在或已停用");
        }
        if (StrUtil.isNotBlank(origin) && !"null".equals(origin) && !originAllowed(a.getDisplayPhone(), origin)) {
            throw exception(AI_LLM_FAIL, "该网站未绑定");
        }
        return a;
    }

    /** 浏览器带来的 Origin 必须是绑定的域名或它的子域名（本机调试 localhost 放行） */
    static boolean originAllowed(String domain, String origin) {
        try {
            String host = StrUtil.nullToEmpty(URI.create(origin).getHost()).toLowerCase(Locale.ROOT);
            host = StrUtil.removePrefix(host, "www.");
            return host.equals(domain) || host.endsWith("." + domain) || "localhost".equals(host) || "127.0.0.1".equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    public SiteConfig config(String key, String origin) {
        ChannelWaAccountDO a = site(key, origin);
        return new SiteConfig(a.getName(), a.getDisplayPhone());
    }

    public VisitorMessage send(String key, String origin, String visitorId, String text, String page) {
        ChannelWaAccountDO a = site(key, origin);
        if (visitorId == null || !visitorId.matches(VISITOR_PATTERN)) {
            throw exception(AI_LLM_FAIL, "访客编号无效");
        }
        String body = StrUtil.trim(StrUtil.nullToEmpty(text));
        if (body.isEmpty()) {
            throw exception(AI_LLM_FAIL, "消息不能为空");
        }
        String content = StrUtil.maxLength(body, MAX_TEXT);
        return TenantUtils.execute(a.getTenantId(), () -> {
            ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), visitorId);
            LocalDateTime now = LocalDateTime.now();
            if (conv == null) {
                conv = ChannelWaConversationDO.builder().accountId(a.getId()).platform(ChannelWaAccountDO.PLATFORM_WEB)
                        .waId(visitorId).unreadCount(0).build();
                conv.setOwnerUserId(channelAssignService.nextOwner(a)); // 按号码的接待成员轮流分配
                conversationMapper.insert(conv);
            } else {
                Long recent = messageMapper.selectCount(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                        .eq(ChannelWaMessageDO::getConversationId, conv.getId())
                        .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_IN)
                        .ge(ChannelWaMessageDO::getMessageTime, now.minusMinutes(1)));
                if (recent >= PER_MINUTE) {
                    throw exception(AI_LLM_FAIL, "发送太频繁，请稍后再试");
                }
            }
            boolean first = conv.getLastInboundTime() == null;
            ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                    .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_IN)
                    .waMessageId("webin_" + IdUtil.fastSimpleUUID()).type("text")
                    .textBody(first && StrUtil.isNotBlank(page) ? content + "\n\n（来自页面：" + StrUtil.maxLength(page.trim(), 300) + "）" : content)
                    .status(ChannelWaMessageDO.STATUS_RECEIVED).messageTime(now).build();
            messageMapper.insert(msg);
            conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId())
                    .setLastInboundTime(now).setLastMessageTime(now)
                    .setLastMessagePreview(StrUtil.maxLength(content, 200))
                    .setUnreadCount((conv.getUnreadCount() == null ? 0 : conv.getUnreadCount()) + 1).setArchived(false));
            accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setLastWebhookTime(now));
            eventPublisher.publishEvent(new ChannelInboundEvent("whatsapp", conv.getId(), msg.getId(), a.getTenantId()));
            return toVisitor(msg);
        });
    }

    /** 访客窗口轮询：返回编号大于 after 的消息，并把发给访客的消息标记为已读 */
    public List<VisitorMessage> messages(String key, String origin, String visitorId, Long after) {
        ChannelWaAccountDO a = site(key, origin);
        if (visitorId == null || !visitorId.matches(VISITOR_PATTERN)) {
            return List.of();
        }
        return TenantUtils.execute(a.getTenantId(), () -> {
            ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), visitorId);
            if (conv == null) {
                return List.<VisitorMessage>of();
            }
            List<ChannelWaMessageDO> list = messageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .eq(ChannelWaMessageDO::getConversationId, conv.getId())
                    .gt(ChannelWaMessageDO::getId, after == null ? 0L : after)
                    .orderByAsc(ChannelWaMessageDO::getId).last("LIMIT 200"));
            if (list.stream().anyMatch(m -> m.getDirection() == ChannelWaMessageDO.DIRECTION_OUT
                    && !ChannelWaMessageDO.STATUS_READ.equals(m.getStatus()))) {
                messageMapper.update(null, new LambdaUpdateWrapper<ChannelWaMessageDO>()
                        .set(ChannelWaMessageDO::getStatus, ChannelWaMessageDO.STATUS_READ)
                        .eq(ChannelWaMessageDO::getConversationId, conv.getId())
                        .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_OUT)
                        .in(ChannelWaMessageDO::getStatus, ChannelWaMessageDO.STATUS_SENT, ChannelWaMessageDO.STATUS_DELIVERED));
            }
            return list.stream().map(ChannelWebImService::toVisitor).toList();
        });
    }

    private static VisitorMessage toVisitor(ChannelWaMessageDO m) {
        String text = m.getTextBody();
        if (m.getDirection() == ChannelWaMessageDO.DIRECTION_IN && text != null) {
            text = text.replaceFirst("\n\n（来自页面：[^\n]*）$", "");
        }
        return new VisitorMessage(m.getId(), m.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "visitor" : "agent",
                m.getType(), text, m.getFileName(), m.getFileUrl(), m.getMessageTime());
    }

}
