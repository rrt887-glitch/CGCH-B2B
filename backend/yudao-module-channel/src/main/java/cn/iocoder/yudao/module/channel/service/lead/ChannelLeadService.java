package cn.iocoder.yudao.module.channel.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAgentSettings;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.yudao.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.yudao.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 线索自动建档：新询盘的发件人既不是客户也没有线索时，在 CRM 中建一条线索并关联到邮件 / 会话。
 * 在 AI 回复之前、同一个租户上下文中调用。
 */
@Service
@Slf4j
public class ChannelLeadService {

    @jakarta.annotation.Resource
    private cn.iocoder.yudao.module.channel.service.routing.ChannelRoutingService channelRoutingService;

    /** crm_customer_source 字典：邮件咨询 10，WhatsApp 11（在 03-channel-seed.sql 中补充） */
    public static final int SOURCE_MAIL = 10;
    public static final int SOURCE_WHATSAPP = 11;
    /** Facebook 主页私信、Instagram 私信 */
    public static final int SOURCE_FACEBOOK = 15;
    public static final int SOURCE_INSTAGRAM = 16;
    /** 官网 IM 在线聊天 */
    public static final int SOURCE_WEB = 17;

    private static int sourceOf(ChannelWaConversationDO c) {
        if (c.isWhatsApp()) {
            return SOURCE_WHATSAPP;
        }
        if (c.isWeb()) {
            return SOURCE_WEB;
        }
        return ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(c.getPlatform()) ? SOURCE_INSTAGRAM : SOURCE_FACEBOOK;
    }

    private static String sourceLabel(Integer source) {
        if (source == null) {
            return "邮件";
        }
        return switch (source) {
            case SOURCE_WHATSAPP -> "WhatsApp";
            case SOURCE_FACEBOOK -> "Facebook";
            case SOURCE_INSTAGRAM -> "Instagram";
            case SOURCE_WEB -> "官网IM";
            default -> "邮件";
        };
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

    @Resource
    private cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService channelNotifyService;
    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelMailService mailService;
    @Resource
    private ChannelWhatsAppService whatsAppService;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private CrmClueMapper crmClueMapper;
    @Resource
    private CrmPermissionService crmPermissionService;

    /** 从询盘中提取的信息 */
    /** level：按「客户评级」规则打的客户级别 1～4，未判断时为 null */
    record Extracted(String company, String countryCode, String contactName, String product, String quantity, Integer level) {
        static final Extracted EMPTY = new Extracted(null, null, null, null, null, null);
    }

    // ==================== 邮件 ====================

    @Transactional(rollbackFor = Exception.class)
    public void handleMail(Long messageId) {
        ChannelMailMessageDO m = mailService.getMessageInternal(messageId);
        if (m == null || m.getDirection() != ChannelMailMessageDO.DIRECTION_IN || m.getCustomerId() != null
                || m.getClueId() != null || StrUtil.isBlank(m.getFromAddress())) {
            return;
        }
        String email = m.getFromAddress().toLowerCase(Locale.ROOT);
        ChannelMailAccountDO account = mailService.getAccountInternal(m.getAccountId());
        if (account == null || email.equalsIgnoreCase(account.getEmail())
                || email.matches("(?i).*(no-?reply|mailer-daemon|postmaster|bounce|notification).*")) {
            return;
        }
        CrmClueDO clue = findClueByEmail(email);
        if (clue == null) {
            ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
            if (cfg == null || !Boolean.TRUE.equals(cfg.getLeadAutoCreate())) {
                return;
            }
            String body = HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), 2000);
            Extracted ex = extract(cfg, "mail", m.getId(), account.getUserId(),
                    "发件人：" + StrUtil.nullToEmpty(m.getFromName()) + " <" + email + ">\n主题：" + StrUtil.nullToEmpty(m.getSubject()) + "\n正文：\n" + body);
            String domain = LeadInference.domainOf(email);
            clue = CrmClueDO.builder()
                    .name(firstNonBlank(ex.contactName(), m.getFromName(), StrUtil.subBefore(email, "@", false)))
                    .email(email)
                    .companyName(firstNonBlank(ex.company(), LeadInference.companyFromDomain(domain)))
                    .website(LeadInference.websiteFromEmail(email))
                    .countryCode(firstNonBlank(ex.countryCode(), LeadInference.countryFromEmail(email)))
                    .source(SOURCE_MAIL)
                    .ownerUserId(account.getUserId())
                    .contactLastTime(m.getSentTime())
                    .contactLastContent(StrUtil.maxLength(StrUtil.nullToEmpty(m.getSubject()) + " | " + StrUtil.nullToEmpty(m.getSnippet()), 500))
                    .level(ex.level())
                    .remark(remark("邮件", m.getSubject(), ex))
                    .build();
            createClue(clue);
        }
        channelMailMessageMapper.updateById(new ChannelMailMessageDO().setId(m.getId()).setClueId(clue.getId()));
    }

    // ==================== WhatsApp ====================

    @Transactional(rollbackFor = Exception.class)
    public void handleWhatsApp(Long conversationId) {
        ChannelWaConversationDO c = whatsAppService.getConversationInternal(conversationId);
        if (c == null || c.getCustomerId() != null || c.getClueId() != null) {
            return;
        }
        ChannelWaAccountDO account = whatsAppService.getAccountInternal(c.getAccountId());
        if (account == null) {
            return;
        }
        boolean wa = c.isWhatsApp();
        CrmClueDO clue = wa ? findClueByWhatsapp(c.getWaId()) : null;
        if (clue == null) {
            ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
            if (cfg == null || !Boolean.TRUE.equals(cfg.getLeadAutoCreate())) {
                return;
            }
            List<ChannelWaMessageDO> recent = whatsAppService.getRecentMessagesInternal(conversationId, 10);
            StringBuilder text = new StringBuilder(c.platformLabel()).append(" 昵称：").append(StrUtil.nullToEmpty(c.getProfileName()))
                    .append(wa ? "\n号码：+" + c.getWaId() : "").append("\n客户消息：\n");
            recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                    .forEach(x -> text.append(StrUtil.nullToEmpty(x.getTextBody())).append('\n'));
            Extracted ex = extract(cfg, "whatsapp", conversationId, account.getUserId(), text.toString());
            String first = recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                    .map(ChannelWaMessageDO::getTextBody).filter(StrUtil::isNotBlank).findFirst().orElse("");
            clue = CrmClueDO.builder()
                    .name(firstNonBlank(ex.contactName(), c.getProfileName(), c.contactLabel()))
                    .whatsapp(wa ? "+" + c.getWaId() : null)
                    .mobile(wa ? "+" + c.getWaId() : null)
                    .companyName(ex.company())
                    .countryCode(firstNonBlank(ex.countryCode(), wa ? LeadInference.countryFromPhone(c.getWaId()) : null))
                    .source(sourceOf(c))
                    .ownerUserId(account.getUserId())
                    .contactLastTime(c.getLastMessageTime())
                    .contactLastContent(StrUtil.maxLength(first, 500))
                    .level(ex.level())
                    .remark(remark(c.platformLabel(), StrUtil.maxLength(first, 80), ex))
                    .build();
            createClue(clue);
        }
        channelWaConversationMapper.updateById(new ChannelWaConversationDO().setId(c.getId()).setClueId(clue.getId()));
    }

    // ==================== 内部 ====================

    private void createClue(CrmClueDO clue) {
        String key = clue.getSource() == null ? "mail" : switch (clue.getSource()) {
            case SOURCE_WHATSAPP -> "whatsapp";
            case SOURCE_FACEBOOK -> "messenger";
            case SOURCE_INSTAGRAM -> "instagram";
            case SOURCE_WEB -> "web";
            default -> "mail";
        };
        Long routed = channelRoutingService.pickOwner(key, clue.getCountryCode());
        if (routed != null) {
            clue.setOwnerUserId(routed); // 命中「消息分流设置」的规则
        }
        clue.setFollowUpStatus(false);
        clue.setTransformStatus(false);
        clue.setAiHosted(true); // 新询盘默认由 AI 接待
        crmClueMapper.insert(clue);
        crmPermissionService.createPermission(new CrmPermissionCreateReqBO()
                .setBizType(CrmBizTypeEnum.CRM_CLUE.getType()).setBizId(clue.getId())
                .setUserId(clue.getOwnerUserId()).setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));
        log.info("[createClue] 自动建档线索 {}：{} {}", clue.getId(), clue.getName(), StrUtil.blankToDefault(clue.getEmail(), clue.getWhatsapp()));
        channelNotifyService.onNewLead(clue.getId(), clue.getName(), clue.getOwnerUserId(),
                sourceLabel(clue.getSource()));
        autoResearch(clue);
    }

    @Resource
    @org.springframework.context.annotation.Lazy
    private cn.iocoder.yudao.module.channel.service.research.ChannelResearchService channelResearchService;

    /** 配技能「自动背调」：新线索有企业官网（或企业邮箱域名）时，建档后自动发起深度背调 */
    private void autoResearch(CrmClueDO clue) {
        ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
        if (cfg == null || !ChannelAgentSettings.skills(cfg).isAutoResearch()) {
            return;
        }
        String site = StrUtil.blankToDefault(StrUtil.trimToNull(clue.getWebsite()), LeadInference.websiteFromEmail(clue.getEmail()));
        if (StrUtil.isBlank(site) || !channelCreditService.hasBalance()) {
            return;
        }
        Runnable start = () -> {
            try {
                channelResearchService.create(clue.getOwnerUserId(), null, null, null, clue.getId());
            } catch (Exception e) {
                log.warn("[autoResearch][线索 {} 自动背调未发起] {}", clue.getId(), e.getMessage());
            }
        };
        // 线索在事务里新建：等事务提交后再背调，后台线程才能读到线索
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            start.run();
                        }
                    });
        } else {
            start.run();
        }
    }

    private CrmClueDO findClueByEmail(String email) {
        List<CrmClueDO> list = crmClueMapper.selectList(new LambdaQueryWrapperX<CrmClueDO>()
                .eq(CrmClueDO::getEmail, email).orderByDesc(CrmClueDO::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 号码可能带空格、横线：末 4 位粗筛后比对纯数字 */
    private CrmClueDO findClueByWhatsapp(String waId) {
        if (StrUtil.length(waId) < 8) {
            return null;
        }
        String tail = waId.substring(waId.length() - 4);
        return crmClueMapper.selectList(new LambdaQueryWrapperX<CrmClueDO>()
                        .and(w -> w.likeLeft(CrmClueDO::getWhatsapp, tail).or().likeLeft(CrmClueDO::getMobile, tail))
                        .orderByDesc(CrmClueDO::getId).last("LIMIT 200"))
                .stream().filter(x -> waId.equals(digits(x.getWhatsapp())) || waId.equals(digits(x.getMobile())))
                .findFirst().orElse(null);
    }

    /** 用 AI 提取公司、国家等；未开启、未配置或余额不足时返回空结果，不影响建档 */
    private Extracted extract(ChannelAiConfigDO cfg, String channel, Long refId, Long userId, String inquiry) {
        if (!Boolean.TRUE.equals(cfg.getLeadAiExtract()) || StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())
                || !channelCreditService.hasBalance()) {
            return Extracted.EMPTY;
        }
        ChannelAgentSettings.Skills skills = ChannelAgentSettings.skills(cfg);
        StringBuilder grade = new StringBuilder();
        if (skills.isAutoGrade() && skills.getGrades() != null && !skills.getGrades().isEmpty()) {
            grade.append("、grade（客户类别，只能是下列编号之一，依据不足时填 null）：");
            for (ChannelAgentSettings.Grade g : skills.getGrades()) {
                grade.append(g.getLevel()).append("=").append(g.getName()).append("（").append(StrUtil.nullToEmpty(g.getDesc())).append("）；");
            }
        }
        String system = "你负责从外贸询盘中提取客户信息。只输出一个 JSON 对象，不要输出其他文字。字段："
                + "company（客户公司名）、country_code（客户所在国家的 ISO 3166-1 二字码）、contact_name（联系人姓名）、"
                + "product（感兴趣的产品）、quantity（数量及单位）" + grade + "。无法确定的字段填 null，不要猜测。Return JSON only.";
        try {
            LlmClient.Result r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(),
                    List.of(LlmClient.Message.system(system), LlmClient.Message.user(StrUtil.maxLength(inquiry, 4000))), 0);
            channelCreditService.consume(channel, refId, userId, r.promptTokens() + r.completionTokens(), "线索信息提取");
            Matcher mt = JSON_BLOCK.matcher(r.content());
            if (!mt.find()) {
                return Extracted.EMPTY;
            }
            JsonNode j = JSON.readTree(mt.group());
            String cc = text(j, "country_code");
            Integer level = null;
            String gv = text(j, "grade");
            if (gv != null && grade.length() > 0) {
                String d = gv.replaceAll("\\D", "");
                int lv = d.isEmpty() ? "ABCD".indexOf(Character.toUpperCase(gv.charAt(0))) + 1 : Integer.parseInt(d.substring(0, 1));
                level = lv >= 1 && lv <= 4 ? lv : null;
            }
            return new Extracted(text(j, "company"), cc != null && COUNTRY.matcher(cc).matches() ? cc.toUpperCase(Locale.ROOT) : null,
                    text(j, "contact_name"), text(j, "product"), text(j, "quantity"), level);
        } catch (Exception e) {
            log.warn("[extract][{} {}] 信息提取失败：{}", channel, refId, e.getMessage());
            return Extracted.EMPTY;
        }
    }

    private static String text(JsonNode j, String field) {
        JsonNode v = j.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return null;
        }
        String s = v.asText().trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : StrUtil.maxLength(s, 200);
    }

    private static String remark(String channel, String subject, Extracted ex) {
        StringBuilder sb = new StringBuilder("由").append(channel).append("询盘自动建档");
        if (StrUtil.isNotBlank(subject)) {
            sb.append("：").append(subject);
        }
        if (ex.product() != null || ex.quantity() != null) {
            sb.append("\n需求：").append(StrUtil.nullToEmpty(ex.product()));
            if (ex.quantity() != null) {
                sb.append("，数量 ").append(ex.quantity());
            }
        }
        return StrUtil.maxLength(sb.toString(), 1000);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StrUtil.isNotBlank(v)) {
                return StrUtil.maxLength(v.trim(), 200);
            }
        }
        return null;
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

}
