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
import cn.iocoder.yudao.module.channel.service.credit.ChannelAiUsageService;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.enums.common.CrmBizTypeEnum;
import cn.iocoder.yudao.module.crm.enums.permission.CrmPermissionLevelEnum;
import cn.iocoder.yudao.module.crm.service.clue.CrmClueService;
import cn.iocoder.yudao.module.crm.service.permission.CrmPermissionService;
import cn.iocoder.yudao.module.crm.service.permission.bo.CrmPermissionCreateReqBO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全渠道进线智能初筛与自动建档服务：
 * 1. 先查重已有正式客户（按邮箱 / WhatsApp 号码），老客户来信直接挂载客户档案并刷新最近联系时间；
 * 2. 企业邮箱按域名检测同企业撞单（同一家海外公司的不同采购人来信自动标记提醒）；
 * 3. 复用系统 AI 设置中的大模型，单次调用同时完成「意图初筛（inquiry / uncertain / spam）+ 客户画像提取 + 评级」；
 *    未配置大模型或超时自动降级至外贸词库与区号/域名规则兜底；
 * 4. 明确询盘（inquiry）自动晋升为正式客户（阶段 1：新询盘）并触发深度背调；模糊问候或疑似推销保留在「待甄别来信」池供人工确认。
 */
@Service
@Slf4j
public class ChannelLeadService {

    @Resource
    private cn.iocoder.yudao.module.channel.service.routing.ChannelRoutingService channelRoutingService;

    /** crm_customer_source 字典：邮件咨询 10，WhatsApp 11 */
    public static final int SOURCE_MAIL = 10;
    public static final int SOURCE_WHATSAPP = 11;
    /** Facebook 主页私信、Instagram 私信 */
    public static final int SOURCE_FACEBOOK = 15;
    public static final int SOURCE_INSTAGRAM = 16;
    /** 官网 IM 在线聊天 */
    public static final int SOURCE_WEB = 17;
    /** YouTube、TikTok 视频评论 */
    public static final int SOURCE_YOUTUBE = 18;
    public static final int SOURCE_TIKTOK = 19;

    public static final String INTENT_INQUIRY = "inquiry";
    public static final String INTENT_UNCERTAIN = "uncertain";
    public static final String INTENT_SPAM = "spam";

    private static int sourceOf(ChannelWaConversationDO c) {
        if (c.isWhatsApp()) {
            return SOURCE_WHATSAPP;
        }
        if (c.isWeb()) {
            return SOURCE_WEB;
        }
        if ("youtube".equals(c.getPlatform())) {
            return SOURCE_YOUTUBE;
        }
        if ("tiktok".equals(c.getPlatform())) {
            return SOURCE_TIKTOK;
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
            case SOURCE_YOUTUBE -> "YouTube";
            case SOURCE_TIKTOK -> "TikTok";
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
    private ChannelAiUsageService channelAiUsageService;
    @Resource
    private CrmClueMapper crmClueMapper;
    @Resource
    private CrmCustomerMapper crmCustomerMapper;
    @Resource
    private CrmClueService crmClueService;
    @Resource
    private CrmPermissionService crmPermissionService;

    /**
     * 单次 AI 提取与初筛结果：
     * intent: inquiry（明确采购询盘） / uncertain（模糊问候待甄别） / spam（推销广告或无关通知）
     */
    record Extracted(
            String intent,
            String company,
            String countryCode,
            String contactName,
            String product,
            String quantity,
            Integer level
    ) {
        static Extracted fallback(String intent) {
            return new Extracted(intent, null, null, null, null, null, null);
        }
    }

    // ==================== 邮件进线 ====================

    @Transactional(rollbackFor = Exception.class)
    public void handleMail(Long messageId) {
        ChannelMailMessageDO m = mailService.getMessageInternal(messageId);
        if (m == null || m.getDirection() != ChannelMailMessageDO.DIRECTION_IN || m.getCustomerId() != null
                || m.getClueId() != null || StrUtil.isBlank(m.getFromAddress())) {
            return;
        }
        String email = m.getFromAddress().toLowerCase(Locale.ROOT).trim();
        ChannelMailAccountDO account = mailService.getAccountInternal(m.getAccountId());
        // 第一层：0 成本规则硬过滤（退信、系统通知、企业自身邮箱直接跳过，不调大模型）
        if (account == null || email.equalsIgnoreCase(account.getEmail())
                || email.matches("(?i).*(no-?reply|do-?not-?reply|mailer-daemon|postmaster|bounce|notification|autoreply).*")) {
            return;
        }

        String summaryLine = StrUtil.maxLength(
                StrUtil.nullToEmpty(m.getSubject()) + " | " + StrUtil.nullToEmpty(m.getSnippet()), 500);

        // 优先查重正式客户表：如果是老客户/已导入客户来信，直接关联客户并更新最近联系动态
        CrmCustomerDO existingCustomer = findCustomerByEmail(email);
        if (existingCustomer != null) {
            touchCustomerLastContact(existingCustomer, m.getSentTime(), summaryLine);
            channelMailMessageMapper.updateById(new ChannelMailMessageDO()
                    .setId(m.getId()).setCustomerId(existingCustomer.getId()));
            return;
        }

        CrmClueDO clue = findClueByEmail(email);
        Long customerId = clue == null ? null : clue.getCustomerId();
        if (clue == null) {
            ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
            boolean autoCreate = cfg != null && Boolean.TRUE.equals(cfg.getLeadAutoCreate());
            boolean keywordInquiry = looksLikeInquiry(m);
            if (!autoCreate && !keywordInquiry) {
                return;
            }
            String body = HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), 2000);
            String fallbackIntent = keywordInquiry ? INTENT_INQUIRY : INTENT_UNCERTAIN;
            Extracted ex = autoCreate && cfg != null
                    ? extract(cfg, "mail", m.getId(), account.getUserId(),
                        "发件人：" + StrUtil.nullToEmpty(m.getFromName()) + " <" + email + ">\n主题："
                                + StrUtil.nullToEmpty(m.getSubject()) + "\n正文：\n" + body,
                        fallbackIntent)
                    : Extracted.fallback(fallbackIntent);

            String domain = LeadInference.domainOf(email);
            CrmCustomerDO domainColleague = findSameDomainCustomer(domain, email);
            clue = CrmClueDO.builder()
                    .name(firstNonBlank(ex.contactName(), m.getFromName(), StrUtil.subBefore(email, "@", false)))
                    .email(email)
                    .companyName(firstNonBlank(ex.company(),
                            domainColleague != null ? domainColleague.getCompanyName() : null,
                            LeadInference.companyFromDomain(domain)))
                    .website(LeadInference.websiteFromEmail(email))
                    .countryCode(firstNonBlank(ex.countryCode(),
                            domainColleague != null ? domainColleague.getCountryCode() : null,
                            LeadInference.countryFromEmail(email)))
                    .source(SOURCE_MAIL)
                    .ownerUserId(account.getUserId())
                    .contactLastTime(m.getSentTime())
                    .contactLastContent(summaryLine)
                    .level(ex.level())
                    .remark(remark("邮件", m.getSubject(), ex, domainColleague, domain))
                    .build();
            customerId = createClueAndMaybePromote(clue, INTENT_INQUIRY.equals(ex.intent()));
        }
        channelMailMessageMapper.updateById(new ChannelMailMessageDO()
                .setId(m.getId()).setClueId(clue.getId()).setCustomerId(customerId));
    }

    // ==================== WhatsApp / 社媒 / 官网 IM 进线 ====================

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
        boolean wa = c.isWhatsApp() && !c.isHiddenNumber();

        List<ChannelWaMessageDO> recent = whatsAppService.getRecentMessagesInternal(conversationId, 10);
        String first = recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                .map(ChannelWaMessageDO::getTextBody).filter(StrUtil::isNotBlank).findFirst().orElse("");

        // 优先查重正式客户表
        if (wa) {
            CrmCustomerDO existingCustomer = findCustomerByWhatsapp(c.getWaId());
            if (existingCustomer != null) {
                touchCustomerLastContact(existingCustomer, c.getLastMessageTime(), StrUtil.maxLength(first, 500));
                channelWaConversationMapper.updateById(new ChannelWaConversationDO()
                        .setId(c.getId()).setCustomerId(existingCustomer.getId()));
                return;
            }
        }

        CrmClueDO clue = wa ? findClueByWhatsapp(c.getWaId()) : null;
        Long customerId = clue == null ? null : clue.getCustomerId();
        if (clue == null) {
            ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
            if (cfg == null || !Boolean.TRUE.equals(cfg.getLeadAutoCreate())) {
                return;
            }
            StringBuilder text = new StringBuilder(c.platformLabel()).append(" 昵称：").append(StrUtil.nullToEmpty(c.getProfileName()))
                    .append(wa ? "\n号码：+" + c.getWaId() : "").append("\n客户消息：\n");
            StringBuilder inboundRaw = new StringBuilder();
            recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                    .forEach(x -> {
                        String body = StrUtil.nullToEmpty(x.getTextBody());
                        text.append(body).append('\n');
                        inboundRaw.append(body).append('\n');
                    });

            String fallbackIntent = looksLikeInquiryText(inboundRaw.toString()) ? INTENT_INQUIRY : INTENT_UNCERTAIN;
            Extracted ex = extract(cfg, "whatsapp", conversationId, account.getUserId(), text.toString(), fallbackIntent);

            clue = CrmClueDO.builder()
                    .name(firstNonBlank(ex.contactName(), c.getProfileName(), c.contactLabel()))
                    .whatsapp(wa ? "+" + c.getWaId() : null)
                    .mobile(wa ? "+" + c.getWaId() : null)
                    .companyName(ex.company())
                    .countryCode(firstNonBlank(ex.countryCode(), wa ? LeadInference.countryFromPhone(c.getWaId()) : null))
                    .source(sourceOf(c))
                    .ownerUserId(c.handlerUserId(account))
                    .contactLastTime(c.getLastMessageTime())
                    .contactLastContent(StrUtil.maxLength(first, 500))
                    .level(ex.level())
                    .remark(remark(c.platformLabel(), StrUtil.maxLength(first, 80), ex, null, null))
                    .build();
            customerId = createClueAndMaybePromote(clue, INTENT_INQUIRY.equals(ex.intent()));
        }
        channelWaConversationMapper.updateById(new ChannelWaConversationDO()
                .setId(c.getId()).setClueId(clue.getId()).setCustomerId(customerId));
    }

    // ==================== 内部建档、自动立项与背调 ====================

    private Long createClueAndMaybePromote(CrmClueDO clue, boolean promoteToCustomer) {
        String key = clue.getSource() == null ? "mail" : switch (clue.getSource()) {
            case SOURCE_WHATSAPP -> "whatsapp";
            case SOURCE_FACEBOOK -> "messenger";
            case SOURCE_INSTAGRAM -> "instagram";
            case SOURCE_WEB -> "web";
            case SOURCE_YOUTUBE -> "youtube";
            case SOURCE_TIKTOK -> "tiktok";
            default -> "mail";
        };
        Long routed = channelRoutingService.pickOwner(key, clue.getCountryCode());
        if (routed != null) {
            clue.setOwnerUserId(routed);
        }
        clue.setFollowUpStatus(false);
        clue.setTransformStatus(false);
        clue.setAiHosted(true);
        crmClueMapper.insert(clue);
        crmPermissionService.createPermission(new CrmPermissionCreateReqBO()
                .setBizType(CrmBizTypeEnum.CRM_CLUE.getType()).setBizId(clue.getId())
                .setUserId(clue.getOwnerUserId()).setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));

        Long customerId = null;
        if (promoteToCustomer) {
            customerId = convertToCustomer(clue);
        }
        log.info("[createClue] 进线建档 clueId={} customerId={} promoted={} name={}",
                clue.getId(), customerId, promoteToCustomer, clue.getName());
        channelNotifyService.onNewLead(clue.getId(), clue.getName(), clue.getOwnerUserId(),
                sourceLabel(clue.getSource()));
        autoResearch(clue, customerId);
        return customerId;
    }

    /** 将明确询盘直接晋升为正式客户（阶段 1：新询盘）；异常时降级保留在线索池，绝不丢单 */
    private Long convertToCustomer(CrmClueDO clue) {
        try {
            crmClueService.transformClue(clue.getId(), clue.getOwnerUserId());
            CrmClueDO latest = crmClueMapper.selectById(clue.getId());
            Long customerId = latest == null ? null : latest.getCustomerId();
            if (customerId != null) {
                crmCustomerMapper.updateById(new CrmCustomerDO().setId(customerId).setStage(1));
            }
            return customerId;
        } catch (Exception e) {
            log.warn("[convertToCustomer][线索 {} 自动晋升新询盘客户失败，保留在待甄别池：{}]", clue.getId(), e.getMessage());
            return null;
        }
    }

    private void touchCustomerLastContact(CrmCustomerDO customer, LocalDateTime msgTime, String content) {
        LocalDateTime ts = msgTime != null ? msgTime : LocalDateTime.now();
        if (customer.getContactLastTime() == null || !ts.isBefore(customer.getContactLastTime())) {
            crmCustomerMapper.updateById(new CrmCustomerDO()
                    .setId(customer.getId())
                    .setContactLastTime(ts)
                    .setContactLastContent(content));
        }
    }

    @Resource
    @org.springframework.context.annotation.Lazy
    private cn.iocoder.yudao.module.channel.service.research.ChannelResearchService channelResearchService;

    /** 配技能「自动背调」：建档后自动拉取官网与公开商业情报，若已晋升为客户则直接挂载至客户档案 */
    private void autoResearch(CrmClueDO clue, Long customerId) {
        ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
        if (cfg == null || !ChannelAgentSettings.skills(cfg).isAutoResearch()) {
            return;
        }
        String site = StrUtil.blankToDefault(StrUtil.trimToNull(clue.getWebsite()), LeadInference.websiteFromEmail(clue.getEmail()));
        if (StrUtil.isBlank(site) || !channelAiUsageService.hasBalance()) {
            return;
        }
        Runnable start = () -> {
            try {
                channelResearchService.create(clue.getOwnerUserId(), null, null, null, null, customerId, clue.getId());
            } catch (Exception e) {
                log.warn("[autoResearch][线索 {} / 客户 {} 自动背调未发起] {}", clue.getId(), customerId, e.getMessage());
            }
        };
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

    // ==================== 规则词库兜底与查重 ====================

    private static final String[] INQUIRY_WORDS_EN = {
            "inquiry", "enquiry", "quotation", "quote", "rfq", "price list", "best price", "unit price",
            "moq", "minimum order", "sample", "catalog", "catalogue", "oem", "odm", "fob", "cif", "exw",
            "lead time", "wholesale", "distributor", "order",
    };
    private static final String[] INQUIRY_WORDS_ZH = {
            "询价", "报价", "采购", "起订量", "打样", "样品", "目录", "画册", "批发", "定制", "交期",
    };

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private static boolean looksLikeInquiry(ChannelMailMessageDO m) {
        String raw = StrUtil.nullToEmpty(m.getSubject()) + "\n" + StrUtil.nullToEmpty(m.getSnippet())
                + "\n" + StrUtil.nullToEmpty(m.getTextBody());
        return looksLikeInquiryText(raw);
    }

    private static boolean looksLikeInquiryText(String raw) {
        String text = URL_PATTERN.matcher(StrUtil.nullToEmpty(raw)).replaceAll(" ").toLowerCase(Locale.ROOT);
        if (StrUtil.isBlank(text)) {
            return false;
        }
        for (String word : INQUIRY_WORDS_EN) {
            if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find()) {
                return true;
            }
        }
        for (String word : INQUIRY_WORDS_ZH) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private CrmCustomerDO findCustomerByEmail(String email) {
        List<CrmCustomerDO> list = crmCustomerMapper.selectList(new LambdaQueryWrapperX<CrmCustomerDO>()
                .eq(CrmCustomerDO::getEmail, email).orderByDesc(CrmCustomerDO::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    /** 同企业域名防撞单检测（忽略公共免费邮箱） */
    private CrmCustomerDO findSameDomainCustomer(String domain, String excludeEmail) {
        if (domain == null || LeadInference.isFreeMail(domain)) {
            return null;
        }
        List<CrmCustomerDO> list = crmCustomerMapper.selectList(new LambdaQueryWrapperX<CrmCustomerDO>()
                .and(w -> w.likeLeft(CrmCustomerDO::getEmail, "@" + domain)
                        .or().like(CrmCustomerDO::getWebsite, domain))
                .orderByDesc(CrmCustomerDO::getId).last("LIMIT 5"));
        return list.stream()
                .filter(c -> c.getEmail() == null || !c.getEmail().equalsIgnoreCase(excludeEmail))
                .findFirst().orElse(null);
    }

    private CrmCustomerDO findCustomerByWhatsapp(String waId) {
        if (StrUtil.length(waId) < 8) {
            return null;
        }
        String tail = waId.substring(waId.length() - 4);
        return crmCustomerMapper.selectList(new LambdaQueryWrapperX<CrmCustomerDO>()
                        .and(w -> w.likeLeft(CrmCustomerDO::getWhatsapp, tail).or().likeLeft(CrmCustomerDO::getMobile, tail))
                        .orderByDesc(CrmCustomerDO::getId).last("LIMIT 200"))
                .stream().filter(x -> waId.equals(digits(x.getWhatsapp())) || waId.equals(digits(x.getMobile())))
                .findFirst().orElse(null);
    }

    private CrmClueDO findClueByEmail(String email) {
        List<CrmClueDO> list = crmClueMapper.selectList(new LambdaQueryWrapperX<CrmClueDO>()
                .eq(CrmClueDO::getEmail, email).orderByDesc(CrmClueDO::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0);
    }

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

    /**
     * 第二层：复用系统 AI 设置中的大模型，单次调用合并完成「初筛分类 + 客户字段提取 + 评级」；
     * 第三层：未开启、未配置密钥或调用异常时自动回退至本地规则意图（fallbackIntent）。
     */
    private Extracted extract(ChannelAiConfigDO cfg, String channel, Long refId, Long userId, String inquiry, String fallbackIntent) {
        if (!Boolean.TRUE.equals(cfg.getLeadAiExtract()) || StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())
                || !channelAiUsageService.hasBalance()) {
            return Extracted.fallback(fallbackIntent);
        }
        ChannelAgentSettings.Skills skills = ChannelAgentSettings.skills(cfg);
        StringBuilder grade = new StringBuilder();
        if (skills.isAutoGrade() && skills.getGrades() != null && !skills.getGrades().isEmpty()) {
            grade.append("、grade（客户类别，只能是下列编号之一，依据不足时填 null）：");
            for (ChannelAgentSettings.Grade g : skills.getGrades()) {
                grade.append(g.getLevel()).append("=").append(g.getName()).append("（").append(StrUtil.nullToEmpty(g.getDesc())).append("）；");
            }
        }
        String system = "你负责对外贸进线消息做意图初筛与客户信息提取。只输出一个 JSON 对象，不要输出其他文字。字段："
                + "intent（只能填 \"inquiry\"、\"uncertain\" 或 \"spam\"：询问产品/价格/MOQ/目录/打样/定制/交期/采购合作填 inquiry；"
                + "仅简单打招呼意图不明填 uncertain；向我方推销货代/SEO/建站/广告营销或系统通知填 spam）、"
                + "company（客户公司名）、country_code（客户所在国家的 ISO 3166-1 二字码）、contact_name（联系人姓名）、"
                + "product（感兴趣的产品）、quantity（数量及单位）" + grade + "。无法确定的字段填 null，不要猜测。Return JSON only.";
        try {
            LlmClient.Result r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(),
                    List.of(LlmClient.Message.system(system), LlmClient.Message.user(StrUtil.maxLength(inquiry, 4000))), 0);
            channelAiUsageService.consume(channel, refId, userId, r.promptTokens() + r.completionTokens(), "进线AI初筛与提取");
            Matcher mt = JSON_BLOCK.matcher(r.content());
            if (!mt.find()) {
                return Extracted.fallback(fallbackIntent);
            }
            JsonNode j = JSON.readTree(mt.group());
            String rawIntent = text(j, "intent");
            String intent = INTENT_SPAM.equalsIgnoreCase(rawIntent) ? INTENT_SPAM
                    : INTENT_UNCERTAIN.equalsIgnoreCase(rawIntent) ? INTENT_UNCERTAIN
                    : INTENT_INQUIRY.equalsIgnoreCase(rawIntent) ? INTENT_INQUIRY
                    : fallbackIntent;
            String cc = text(j, "country_code");
            Integer level = null;
            String gv = text(j, "grade");
            if (gv != null && grade.length() > 0) {
                String d = gv.replaceAll("\\D", "");
                int lv = d.isEmpty() ? "ABCD".indexOf(Character.toUpperCase(gv.charAt(0))) + 1 : Integer.parseInt(d.substring(0, 1));
                level = lv >= 1 && lv <= 4 ? lv : null;
            }
            return new Extracted(
                    intent,
                    text(j, "company"),
                    cc != null && COUNTRY.matcher(cc).matches() ? cc.toUpperCase(Locale.ROOT) : null,
                    text(j, "contact_name"),
                    text(j, "product"),
                    text(j, "quantity"),
                    level
            );
        } catch (Exception e) {
            log.warn("[extract][{} {}] AI 初筛与提取失败，使用规则兜底：{}", channel, refId, e.getMessage());
            return Extracted.fallback(fallbackIntent);
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

    private static String remark(String channel, String subject, Extracted ex, CrmCustomerDO domainColleague, String domain) {
        StringBuilder sb = new StringBuilder();
        // 写入结构化商务黑板头，前端 360° 抽屉与 AI 助手均可直接读取意向产品与数量
        if (ex.product() != null || ex.quantity() != null) {
            String pq = StrUtil.nullToEmpty(ex.product())
                    + (ex.quantity() != null ? (ex.product() != null ? " · " : "") + ex.quantity() : "");
            sb.append("[商务黑板] 产品:").append(pq.replace("|", "/").replace("\n", " ")).append('\n');
        }
        if (INTENT_SPAM.equals(ex.intent())) {
            sb.append("[AI初筛:疑似推销] ");
        } else if (INTENT_UNCERTAIN.equals(ex.intent())) {
            sb.append("[AI初筛:待确认意向] ");
        }
        if (domainColleague != null && domain != null) {
            sb.append("[同企防撞单] 域名 ").append(domain).append(" 已存在客户「")
                    .append(domainColleague.getName()).append("」，请协同跟进避免重复报价。\n");
        }
        sb.append("由").append(channel).append("自动建档");
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
