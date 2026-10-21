package cn.iocoder.yudao.module.channel.service.policy;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSendPolicyDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSuppressionDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.policy.ChannelSendPolicyMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.policy.ChannelSuppressionMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 发送策略：发送前检查退订 / 黑名单和发送频率；收到消息时识别退订请求和退信。
 * 目的是保护邮箱和 WhatsApp 号码的发信信誉，避免被服务商限制或封禁。
 */
@Service
@Slf4j
public class ChannelSendPolicyService {

    public static final String CHANNEL_MAIL = "mail";
    public static final String CHANNEL_WHATSAPP = "whatsapp";
    static final String DEFAULT_FOOTER = "If you would prefer not to receive emails like this, simply reply \"unsubscribe\" and we will remove you.";

    /** 退订用语（只看客户本次写的内容，不看引用的历史邮件） */
    private static final Pattern UNSUBSCRIBE = Pattern.compile(
            "(?i)(\\bunsubscribe\\b|\\bopt[- ]?out\\b|remove me from (your|the) (list|mailing)|stop (sending|emailing|messaging) me|"
                    + "do not (contact|email|message) me|退订|取消订阅|不要再(给我)?发|别再发)");
    /** WhatsApp 的退订一般是单独一句 STOP */
    private static final Pattern WA_STOP = Pattern.compile("(?i)^\\s*(stop|unsubscribe|退订|取消订阅)\\s*[.!。！]?\\s*$");
    private static final Pattern QUOTE_START = Pattern.compile(
            "(?im)^(>|On .{3,120} wrote:|-{2,}\\s*Original Message|From:\\s|发件人[:：]|在 .{3,80} 写道[:：])");
    private static final Pattern BOUNCE_FROM = Pattern.compile("(?i)(mailer-daemon|postmaster|mail delivery)");
    private static final Pattern BOUNCE_SUBJECT = Pattern.compile(
            "(?i)(undeliver|delivery (status notification|has failed|failure)|returned mail|failure notice|退信|无法投递|投递失败)");
    private static final Pattern FINAL_RECIPIENT = Pattern.compile("(?i)(?:Final|Original)-Recipient:\\s*rfc822;\\s*<?([^\\s<>;]+@[^\\s<>;]+)>?");
    private static final Pattern ANGLE_ADDR = Pattern.compile("<([^\\s<>]+@[^\\s<>]+)>:");

    @Resource
    private ChannelSendPolicyMapper channelSendPolicyMapper;
    @Resource
    private ChannelSuppressionMapper channelSuppressionMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;

    // ==================== 配置 ====================

    public ChannelSendPolicyDO getPolicy() {
        ChannelSendPolicyDO p = channelSendPolicyMapper.selectCurrent();
        return p != null ? p : ChannelSendPolicyDO.builder().mailHourlyLimit(50).mailDailyLimit(300).waHourlyLimit(200)
                .waDailyLimit(1000).applyToManual(false).autoUnsubscribe(true).autoBounce(true).unsubscribeFooter(true)
                .footerText(DEFAULT_FOOTER).build();
    }

    public void savePolicy(ChannelSendPolicyDO req) {
        for (Integer v : new Integer[]{req.getMailHourlyLimit(), req.getMailDailyLimit(), req.getWaHourlyLimit(), req.getWaDailyLimit()}) {
            if (v == null || v < 0 || v > 100_000) {
                throw exception(SEND_POLICY_INVALID, "发送上限需在 0~100000 之间（0 表示不限）");
            }
        }
        req.setApplyToManual(Boolean.TRUE.equals(req.getApplyToManual()));
        req.setAutoUnsubscribe(Boolean.TRUE.equals(req.getAutoUnsubscribe()));
        req.setAutoBounce(Boolean.TRUE.equals(req.getAutoBounce()));
        req.setUnsubscribeFooter(Boolean.TRUE.equals(req.getUnsubscribeFooter()));
        req.setFooterText(StrUtil.maxLength(StrUtil.blankToDefault(StrUtil.trim(req.getFooterText()), DEFAULT_FOOTER), 500));
        ChannelSendPolicyDO old = channelSendPolicyMapper.selectCurrent();
        if (old == null) {
            req.setId(null);
            channelSendPolicyMapper.insert(req);
        } else {
            req.setId(old.getId());
            channelSendPolicyMapper.updateById(req);
        }
    }

    // ==================== 发送前检查 ====================

    static String normalizeMail(String s) {
        return StrUtil.trim(StrUtil.nullToEmpty(s)).toLowerCase(Locale.ROOT);
    }

    static String digits(String s) {
        return StrUtil.nullToEmpty(s).replaceAll("\\D", "");
    }

    /**
     * 邮件发送前检查。
     *
     * @param automated AI 自动回复、AI 起草或批量发送；频率上限默认只限制这类发送
     */
    public void checkMail(Long accountId, Collection<String> recipients, boolean automated) {
        Set<String> keys = new LinkedHashSet<>();
        for (String r : recipients) {
            String e = normalizeMail(r);
            keys.add(e);
            if (e.contains("@")) {
                keys.add(e.substring(e.indexOf('@')));
            }
        }
        if (!keys.isEmpty()) {
            List<ChannelSuppressionDO> hit = channelSuppressionMapper.selectMatches(CHANNEL_MAIL, keys);
            if (!hit.isEmpty()) {
                throw exception(SEND_SUPPRESSED, hit.get(0).getAddress());
            }
        }
        ChannelSendPolicyDO p = getPolicy();
        if (!automated && !Boolean.TRUE.equals(p.getApplyToManual())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        limit(p.getMailHourlyLimit(), () -> mailCount(accountId, now.minusHours(1)), "该邮箱最近 1 小时", recipients.size());
        limit(p.getMailDailyLimit(), () -> mailCount(accountId, now.minusDays(1)), "该邮箱最近 24 小时", recipients.size());
    }

    public void checkWhatsApp(Long accountId, String waId, boolean automated) {
        String d = digits(waId);
        if (!d.isEmpty() && channelSuppressionMapper.selectOne(CHANNEL_WHATSAPP, d) != null) {
            throw exception(SEND_SUPPRESSED, "+" + d);
        }
        ChannelSendPolicyDO p = getPolicy();
        if (!automated && !Boolean.TRUE.equals(p.getApplyToManual())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        limit(p.getWaHourlyLimit(), () -> waCount(accountId, now.minusHours(1)), "该 WhatsApp 号码最近 1 小时", 1);
        limit(p.getWaDailyLimit(), () -> waCount(accountId, now.minusDays(1)), "该 WhatsApp 号码最近 24 小时", 1);
    }

    private static void limit(Integer max, java.util.function.LongSupplier used, String what, int adding) {
        if (max == null || max <= 0) {
            return;
        }
        long n = used.getAsLong();
        if (n + adding > max) {
            throw exception(SEND_RATE_LIMITED, what + "已发送 " + n + " 条，达到上限 " + max + " 条，请稍后再发（可在发送策略中调整）");
        }
    }

    private long mailCount(Long accountId, LocalDateTime since) {
        return channelMailMessageMapper.selectCount(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .eq(ChannelMailMessageDO::getAccountId, accountId).eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_OUT)
                .ne(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_DRAFT).ge(ChannelMailMessageDO::getSentTime, since));
    }

    private long waCount(Long accountId, LocalDateTime since) {
        return channelWaMessageMapper.selectCount(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                .eq(ChannelWaMessageDO::getAccountId, accountId).eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_OUT)
                .ge(ChannelWaMessageDO::getMessageTime, since));
    }

    /** 批量邮件末尾的退订说明（关闭时原样返回） */
    public String withFooter(String text) {
        ChannelSendPolicyDO p = getPolicy();
        if (!Boolean.TRUE.equals(p.getUnsubscribeFooter()) || StrUtil.isBlank(p.getFooterText())) {
            return text;
        }
        return StrUtil.nullToEmpty(text).stripTrailing() + "\n\n--\n" + p.getFooterText();
    }

    /** 各账号最近 1 小时 / 24 小时的发送量 */
    public List<Map<String, Object>> usage() {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChannelMailAccountDO a : channelMailAccountMapper.selectList()) {
            out.add(Map.of("channel", CHANNEL_MAIL, "accountId", a.getId(), "name", a.getEmail(), "userId", a.getUserId(),
                    "hour", mailCount(a.getId(), now.minusHours(1)), "day", mailCount(a.getId(), now.minusDays(1))));
        }
        for (ChannelWaAccountDO a : channelWaAccountMapper.selectList()) {
            out.add(Map.of("channel", CHANNEL_WHATSAPP, "accountId", a.getId(),
                    "name", StrUtil.blankToDefault(a.getDisplayPhone(), a.getName()), "userId", a.getUserId(),
                    "hour", waCount(a.getId(), now.minusHours(1)), "day", waCount(a.getId(), now.minusDays(1))));
        }
        return out;
    }

    // ==================== 收信识别 ====================

    /** 只保留客户本次写的内容：去掉引用的历史邮件 */
    static String ownText(String text) {
        String s = StrUtil.nullToEmpty(text);
        Matcher m = QUOTE_START.matcher(s);
        return m.find() ? s.substring(0, m.start()) : s;
    }

    static boolean isUnsubscribe(String subject, String body) {
        return UNSUBSCRIBE.matcher(StrUtil.nullToEmpty(subject)).find()
                || UNSUBSCRIBE.matcher(StrUtil.maxLength(ownText(body), 1500)).find();
    }

    /**
     * 收到消息时调用；识别为退订或退信时加入名单并返回 true（调用方不再建线索、不再让 AI 回复）。
     */
    public boolean onInbound(String channel, Long refId, Long messageId) {
        ChannelSendPolicyDO p = getPolicy();
        if (CHANNEL_MAIL.equals(channel)) {
            ChannelMailMessageDO m = channelMailMessageMapper.selectById(messageId);
            if (m == null || m.getDirection() != ChannelMailMessageDO.DIRECTION_IN) {
                return false;
            }
            String body = StrUtil.isNotBlank(m.getTextBody()) ? m.getTextBody() : HtmlText.toText(m.getHtmlBody());
            if (BOUNCE_FROM.matcher(StrUtil.nullToEmpty(m.getFromAddress()) + " " + StrUtil.nullToEmpty(m.getFromName())).find()
                    && BOUNCE_SUBJECT.matcher(StrUtil.nullToEmpty(m.getSubject())).find()) {
                if (Boolean.TRUE.equals(p.getAutoBounce())) {
                    for (String addr : bouncedAddresses(body, m.getAccountId())) {
                        add(CHANNEL_MAIL, addr, ChannelSuppressionDO.REASON_BOUNCE, "退信：" + StrUtil.maxLength(m.getSubject(), 100), null);
                    }
                }
                return true; // 退信本身不需要建线索或回复
            }
            if (Boolean.TRUE.equals(p.getAutoUnsubscribe()) && isUnsubscribe(m.getSubject(), body)) {
                add(CHANNEL_MAIL, m.getFromAddress(), ChannelSuppressionDO.REASON_UNSUBSCRIBE,
                        StrUtil.maxLength(StrUtil.trim(ownText(body)), 200), null);
                return true;
            }
            return false;
        }
        ChannelWaMessageDO wm = channelWaMessageMapper.selectById(messageId);
        if (wm == null || !Boolean.TRUE.equals(p.getAutoUnsubscribe()) || StrUtil.isBlank(wm.getTextBody())) {
            return false;
        }
        if (WA_STOP.matcher(wm.getTextBody()).matches() || UNSUBSCRIBE.matcher(wm.getTextBody()).find()) {
            ChannelWaConversationDO c = channelWaConversationMapper.selectById(wm.getConversationId());
            if (c != null) {
                add(CHANNEL_WHATSAPP, c.getWaId(), ChannelSuppressionDO.REASON_UNSUBSCRIBE, StrUtil.maxLength(wm.getTextBody(), 200), null);
                return true;
            }
        }
        return false;
    }

    /** 从退信正文里找出投递失败的地址（排除我方邮箱） */
    private List<String> bouncedAddresses(String body, Long accountId) {
        ChannelMailAccountDO a = channelMailAccountMapper.selectById(accountId);
        String own = a == null ? "" : normalizeMail(a.getEmail());
        Set<String> out = new LinkedHashSet<>();
        for (Pattern pt : new Pattern[]{FINAL_RECIPIENT, ANGLE_ADDR}) {
            Matcher m = pt.matcher(StrUtil.nullToEmpty(body));
            while (m.find()) {
                String e = normalizeMail(m.group(1));
                if (!e.equals(own) && !BOUNCE_FROM.matcher(e).find()) {
                    out.add(e);
                }
            }
        }
        return new ArrayList<>(out);
    }

    // ==================== 名单维护 ====================

    /** 加入名单（已存在则忽略），返回记录 */
    public ChannelSuppressionDO add(String channel, String value, String reason, String remark, Long userId) {
        String ch = CHANNEL_WHATSAPP.equals(channel) ? CHANNEL_WHATSAPP : CHANNEL_MAIL;
        String v = CHANNEL_WHATSAPP.equals(ch) ? digits(value) : normalizeMail(value);
        if (CHANNEL_MAIL.equals(ch) && !v.isEmpty() && !v.contains("@")) {
            v = "@" + v; // 只填了域名
        }
        if (v.isEmpty() || v.length() < 3 || (CHANNEL_MAIL.equals(ch) && v.endsWith("@"))) {
            throw exception(SEND_POLICY_INVALID, "请填写正确的邮箱、@域名或 WhatsApp 号码");
        }
        ChannelSuppressionDO old = channelSuppressionMapper.selectOne(ch, v);
        if (old != null) {
            return old;
        }
        ChannelSuppressionDO s = ChannelSuppressionDO.builder().channel(ch).address(v)
                .reason(StrUtil.blankToDefault(reason, ChannelSuppressionDO.REASON_MANUAL)).remark(StrUtil.maxLength(remark, 250))
                .userId(userId).build();
        channelSuppressionMapper.insert(s);
        log.info("[add][{} {} 加入名单：{}]", ch, v, s.getReason());
        return s;
    }

    public PageResult<ChannelSuppressionDO> page(PageParam req, String channel, String reason, String keyword) {
        return channelSuppressionMapper.selectPage(req, StrUtil.emptyToNull(channel), StrUtil.emptyToNull(reason), StrUtil.emptyToNull(keyword));
    }

    public void remove(Long id) {
        channelSuppressionMapper.deleteById(id);
    }

}
