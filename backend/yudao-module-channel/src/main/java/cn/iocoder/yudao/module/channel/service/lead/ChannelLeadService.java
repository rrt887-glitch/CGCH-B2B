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

    /** crm_customer_source 字典：邮件咨询 10，WhatsApp 11（在 03-channel-seed.sql 中补充） */
    public static final int SOURCE_MAIL = 10;
    public static final int SOURCE_WHATSAPP = 11;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Za-z]{2}$");

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
    record Extracted(String company, String countryCode, String contactName, String product, String quantity) {
        static final Extracted EMPTY = new Extracted(null, null, null, null, null);
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
        CrmClueDO clue = findClueByWhatsapp(c.getWaId());
        if (clue == null) {
            ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
            if (cfg == null || !Boolean.TRUE.equals(cfg.getLeadAutoCreate())) {
                return;
            }
            List<ChannelWaMessageDO> recent = whatsAppService.getRecentMessagesInternal(conversationId, 10);
            StringBuilder text = new StringBuilder("WhatsApp 昵称：").append(StrUtil.nullToEmpty(c.getProfileName()))
                    .append("\n号码：+").append(c.getWaId()).append("\n客户消息：\n");
            recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                    .forEach(x -> text.append(StrUtil.nullToEmpty(x.getTextBody())).append('\n'));
            Extracted ex = extract(cfg, "whatsapp", conversationId, account.getUserId(), text.toString());
            String first = recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                    .map(ChannelWaMessageDO::getTextBody).filter(StrUtil::isNotBlank).findFirst().orElse("");
            clue = CrmClueDO.builder()
                    .name(firstNonBlank(ex.contactName(), c.getProfileName(), "+" + c.getWaId()))
                    .whatsapp("+" + c.getWaId())
                    .mobile("+" + c.getWaId())
                    .companyName(ex.company())
                    .countryCode(firstNonBlank(ex.countryCode(), LeadInference.countryFromPhone(c.getWaId())))
                    .source(SOURCE_WHATSAPP)
                    .ownerUserId(account.getUserId())
                    .contactLastTime(c.getLastMessageTime())
                    .contactLastContent(StrUtil.maxLength(first, 500))
                    .remark(remark("WhatsApp", StrUtil.maxLength(first, 80), ex))
                    .build();
            createClue(clue);
        }
        channelWaConversationMapper.updateById(new ChannelWaConversationDO().setId(c.getId()).setClueId(clue.getId()));
    }

    // ==================== 内部 ====================

    private void createClue(CrmClueDO clue) {
        clue.setFollowUpStatus(false);
        clue.setTransformStatus(false);
        clue.setAiHosted(true); // 新询盘默认由 AI 接待
        crmClueMapper.insert(clue);
        crmPermissionService.createPermission(new CrmPermissionCreateReqBO()
                .setBizType(CrmBizTypeEnum.CRM_CLUE.getType()).setBizId(clue.getId())
                .setUserId(clue.getOwnerUserId()).setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));
        log.info("[createClue] 自动建档线索 {}：{} {}", clue.getId(), clue.getName(), StrUtil.blankToDefault(clue.getEmail(), clue.getWhatsapp()));
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
        String system = "你负责从外贸询盘中提取客户信息。只输出一个 JSON 对象，不要输出其他文字。字段："
                + "company（客户公司名）、country_code（客户所在国家的 ISO 3166-1 二字码）、contact_name（联系人姓名）、"
                + "product（感兴趣的产品）、quantity（数量及单位）。无法确定的字段填 null，不要猜测。Return JSON only.";
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
            return new Extracted(text(j, "company"), cc != null && COUNTRY.matcher(cc).matches() ? cc.toUpperCase(Locale.ROOT) : null,
                    text(j, "contact_name"), text(j, "product"), text(j, "quantity"));
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
