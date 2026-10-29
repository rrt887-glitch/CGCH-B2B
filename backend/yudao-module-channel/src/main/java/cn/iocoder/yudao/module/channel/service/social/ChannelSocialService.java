package cn.iocoder.yudao.module.channel.service.social;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 评论渠道：YouTube 频道、TikTok 企业号。授权后定时拉取视频下的评论，每条顶层评论是「会话」里的一个对话，
 * 客户的评论触发 AI 回复与线索建档（与 WhatsApp 相同的流程），回复以评论回复的形式发出。
 * 账号存在 channel_wa_account（platform = youtube / tiktok）：phoneNumberId 为频道编号 / open_id，
 * displayPhone 为频道名称 / @用户名，accessToken 加密保存刷新令牌。
 */
@Service
@Slf4j
public class ChannelSocialService {

    public static final String YOUTUBE = "youtube";
    public static final String TIKTOK = "tiktok";
    private static final String STATE_KEY = "cgch:social-oauth:state:";

    @Resource
    private ChannelOAuthProperties channelOAuthProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /** 访问令牌缓存：账号编号 → 令牌与过期时间 */
    private final Map<Long, Object[]> tokens = new ConcurrentHashMap<>();

    public static boolean isSocial(String platform) {
        return YOUTUBE.equals(platform) || TIKTOK.equals(platform);
    }

    private ChannelOAuthProperties.Social cfg(String platform) {
        return YOUTUBE.equals(platform) ? channelOAuthProperties.getYoutube() : channelOAuthProperties.getTiktok();
    }

    private String redirectUri(String platform) {
        return StrUtil.removeSuffix(channelOAuthProperties.getRedirectBase(), "/") + cfg(platform).getCallbackPath();
    }

    public List<String> enabledPlatforms() {
        List<String> r = new ArrayList<>();
        if (channelOAuthProperties.getYoutube().configured()) {
            r.add(YOUTUBE);
        }
        if (channelOAuthProperties.getTiktok().configured()) {
            r.add(TIKTOK);
        }
        return r;
    }

    // ==================== 授权 ====================

    public String authorizeUrl(Long userId, String platform, String hint) {
        if (!isSocial(platform) || !cfg(platform).configured()) {
            throw exception(AI_LLM_FAIL, (YOUTUBE.equals(platform) ? "YouTube" : "TikTok") + " 授权应用还没有配置，请联系管理员");
        }
        String state = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(STATE_KEY + state, TenantContextHolder.getRequiredTenantId() + ":" + userId + ":"
                + platform + ":" + StrUtil.maxLength(StrUtil.nullToEmpty(hint).replace(":", "").replace("@", "").trim(), 60), 10, TimeUnit.MINUTES);
        return YOUTUBE.equals(platform) ? SocialClient.youtubeAuthorizeUrl(cfg(platform), redirectUri(platform), state)
                : SocialClient.tiktokAuthorizeUrl(cfg(platform), state);
    }

    /** 授权回调：换令牌、取频道 / 账号信息、建立或更新渠道账号；返回账号名称 */
    public String callback(String platform, String code, String state, String error) {
        if (StrUtil.isNotBlank(error)) {
            throw exception(AI_LLM_FAIL, "授权被取消或拒绝：" + error);
        }
        String saved = StrUtil.isBlank(state) ? null : stringRedisTemplate.opsForValue().getAndDelete(STATE_KEY + state);
        if (saved == null) {
            throw exception(AI_LLM_FAIL, "授权链接已过期，请重新发起授权");
        }
        String[] p = saved.split(":", 4);
        if (!platform.equals(p[2])) {
            throw exception(AI_LLM_FAIL, "授权平台不匹配");
        }
        long tenantId = Long.parseLong(p[0]);
        long userId = Long.parseLong(p[1]);
        ChannelOAuthProperties.Social c = cfg(platform);
        String accountId;
        String name;
        SocialClient.Token t;
        try {
            if (YOUTUBE.equals(platform)) {
                t = SocialClient.youtubeExchange(c, code, redirectUri(platform));
                String[] ch = SocialClient.youtubeMyChannel(c, t.access());
                accountId = ch[0];
                name = ch[1];
            } else {
                t = SocialClient.tiktokExchange(c, code, redirectUri(platform));
                accountId = t.openId();
                String[] prof = SocialClient.tiktokProfile(c, t.access(), t.openId());
                name = StrUtil.isNotBlank(prof[0]) ? "@" + prof[0] : StrUtil.blankToDefault(prof[1], "TikTok");
                String hint = p.length > 3 ? p[3] : "";
                if (StrUtil.isNotBlank(hint) && StrUtil.isNotBlank(prof[0]) && !hint.equalsIgnoreCase(prof[0])) {
                    log.info("[callback][TikTok 授权账号 {} 与填写的用户名 {} 不一致，以授权账号为准]", prof[0], hint);
                }
            }
        } catch (WaGraphClient.WaApiException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
        if (StrUtil.isBlank(t.refresh()) || StrUtil.isBlank(accountId)) {
            throw exception(AI_LLM_FAIL, "没有拿到长期授权，请重新授权并同意全部权限");
        }
        final String fAccountId = accountId;
        final String fName = name;
        final String fRefresh = t.refresh();
        TenantUtils.execute(tenantId, () -> {
            ChannelWaAccountDO old = channelWaAccountMapper.selectOne(new LambdaQueryWrapperX<ChannelWaAccountDO>()
                    .eq(ChannelWaAccountDO::getPlatform, platform).eq(ChannelWaAccountDO::getPhoneNumberId, fAccountId));
            ChannelWaAccountDO a = ChannelWaAccountDO.builder().platform(platform).authType(ChannelWaAccountDO.AUTH_OAUTH)
                    .phoneNumberId(fAccountId).displayPhone(fName).name(fName).accessToken(fRefresh)
                    .verifyToken("-").enabled(true).build();
            if (old == null) {
                a.setUserId(userId);
                channelWaAccountMapper.insert(a);
            } else {
                a.setId(old.getId());
                channelWaAccountMapper.updateById(a);
                tokens.remove(old.getId());
            }
        });
        log.info("[callback][租户 {} 授权 {} 账号 {}]", tenantId, platform, name);
        return name;
    }

    private String access(ChannelWaAccountDO a) {
        Object[] cached = tokens.get(a.getId());
        if (cached != null && (Long) cached[1] > System.currentTimeMillis() + 60_000) {
            return (String) cached[0];
        }
        ChannelOAuthProperties.Social c = cfg(a.getPlatform());
        SocialClient.Token t = YOUTUBE.equals(a.getPlatform()) ? SocialClient.youtubeRefresh(c, a.getAccessToken())
                : SocialClient.tiktokRefresh(c, a.getAccessToken());
        if (StrUtil.isBlank(t.access())) {
            throw new WaGraphClient.WaApiException("授权已失效，请重新授权");
        }
        if (TIKTOK.equals(a.getPlatform()) && StrUtil.isNotBlank(t.refresh()) && !t.refresh().equals(a.getAccessToken())) {
            channelWaAccountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setAccessToken(t.refresh()));
            a.setAccessToken(t.refresh());
        }
        tokens.put(a.getId(), new Object[]{t.access(), System.currentTimeMillis() + t.expiresIn() * 1000});
        return t.access();
    }

    // ==================== 回复 ====================

    /** 回复评论（对话里的顶层评论），返回新评论编号 */
    public String reply(ChannelWaAccountDO a, ChannelWaConversationDO conv, String text) {
        String access = access(a);
        if (YOUTUBE.equals(a.getPlatform())) {
            return SocialClient.youtubeReply(cfg(YOUTUBE), access, conv.getWaId(), text);
        }
        String[] key = conv.getWaId().split(":", 2); // 视频编号:顶层评论编号
        return SocialClient.tiktokReply(cfg(TIKTOK), access, a.getPhoneNumberId(), key[0], key.length > 1 ? key[1] : key[0], text);
    }

    // ==================== 拉取评论 ====================

    /** 拉取所有租户的评论渠道；返回新入库的客户评论数 */
    public int pollAll() {
        List<ChannelWaAccountDO> accounts = TenantUtils.executeIgnore(() -> channelWaAccountMapper.selectList(
                new LambdaQueryWrapperX<ChannelWaAccountDO>().in(ChannelWaAccountDO::getPlatform, YOUTUBE, TIKTOK)
                        .eq(ChannelWaAccountDO::getEnabled, true)));
        int n = 0;
        for (ChannelWaAccountDO a : accounts) {
            try {
                n += TenantUtils.execute(a.getTenantId(), () -> poll(a));
            } catch (Exception e) {
                log.warn("[pollAll][{} 账号 {} 拉取评论失败] {}", a.getPlatform(), a.getId(), e.getMessage());
            }
        }
        return n;
    }

    /** 首次拉取只导入历史，不触发 AI 回复；之后新的客户评论触发 AI 回复与线索建档 */
    public synchronized int poll(ChannelWaAccountDO a) {
        boolean first = a.getLastWebhookTime() == null;
        String access = access(a);
        List<SocialClient.Comment> comments = YOUTUBE.equals(a.getPlatform())
                ? SocialClient.youtubeComments(cfg(YOUTUBE), access, a.getPhoneNumberId())
                : SocialClient.tiktokComments(cfg(TIKTOK), access, a.getPhoneNumberId());
        comments.sort(Comparator.comparingLong(SocialClient.Comment::time));
        Map<String, SocialClient.Comment> tops = new HashMap<>();
        comments.stream().filter(c -> c.parentId() == null).forEach(c -> tops.put(c.id(), c));
        int fresh = 0;
        for (SocialClient.Comment c : comments) {
            String topId = c.parentId() == null ? c.id() : c.parentId();
            String waId = YOUTUBE.equals(a.getPlatform()) ? topId : c.videoId() + ":" + topId;
            if (channelWaMessageMapper.selectByWaMessageId(a.getId(), c.id()) != null) {
                continue;
            }
            SocialClient.Comment top = tops.getOrDefault(topId, c);
            ChannelWaConversationDO conv = channelWaConversationMapper.selectByAccountIdAndWaId(a.getId(), waId);
            if (conv == null) {
                conv = ChannelWaConversationDO.builder().accountId(a.getId()).platform(a.getPlatform()).waId(waId)
                        .profileName(top.own() ? null : StrUtil.maxLength(top.author(), 100)).unreadCount(0).build();
                channelWaConversationMapper.insert(conv);
            }
            LocalDateTime time = LocalDateTime.ofInstant(Instant.ofEpochSecond(c.time()), ZoneId.systemDefault());
            String body = c.parentId() == null && StrUtil.isNotBlank(c.videoTitle())
                    ? "（评论视频《" + c.videoTitle() + "》）" + c.text() : c.text();
            ChannelWaMessageDO msg = ChannelWaMessageDO.builder().accountId(a.getId()).conversationId(conv.getId())
                    .direction(c.own() ? ChannelWaMessageDO.DIRECTION_OUT : ChannelWaMessageDO.DIRECTION_IN)
                    .waMessageId(c.id()).type("text").textBody(StrUtil.maxLength(body, 4000))
                    .status(c.own() ? ChannelWaMessageDO.STATUS_SENT : ChannelWaMessageDO.STATUS_RECEIVED).messageTime(time)
                    .aiGenerated(c.own() ? false : null).build();
            channelWaMessageMapper.insert(msg);
            ChannelWaConversationDO upd = new ChannelWaConversationDO().setId(conv.getId())
                    .setLastMessageTime(max(conv.getLastMessageTime(), time)).setLastMessagePreview(StrUtil.maxLength(c.text(), 200));
            if (!c.own()) {
                upd.setLastInboundTime(max(conv.getLastInboundTime(), time));
                upd.setArchived(false); // 客户来新消息：自动取消归档
                if (!first) {
                    upd.setUnreadCount((conv.getUnreadCount() == null ? 0 : conv.getUnreadCount()) + 1);
                }
            }
            channelWaConversationMapper.updateById(upd);
            if (!c.own() && !first) {
                fresh++;
                eventPublisher.publishEvent(new ChannelInboundEvent("whatsapp", conv.getId(), msg.getId(), a.getTenantId()));
            }
        }
        channelWaAccountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setLastWebhookTime(LocalDateTime.now()));
        a.setLastWebhookTime(LocalDateTime.now());
        return fresh;
    }

    private static LocalDateTime max(LocalDateTime x, LocalDateTime y) {
        return x == null ? y : y == null ? x : x.isAfter(y) ? x : y;
    }

}
