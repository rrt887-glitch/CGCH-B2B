package cn.iocoder.yudao.module.channel.service.lead;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.lead.ChannelLeadRuleDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.lead.ChannelLeadRuleMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService;
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

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.LEAD_RULE_INVALID;

/**
 * 留资邮箱：阿里国际站、中国制造网等 B2B 平台没有开放询盘消息接口，但都会把新询盘以通知邮件发到卖家邮箱；
 * 官网的联系表单通常也以邮件通知。按规则识别这类通知邮件，解析出买家信息自动建线索（不让 AI 回复通知邮件）。
 */
@Service
@Slf4j
public class ChannelPlatformLeadService {

    public static final int SOURCE_ALIBABA = 12;
    public static final int SOURCE_MADE_IN_CHINA = 13;
    public static final int SOURCE_WEBSITE_FORM = 14;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern JSON_BLOCK = Pattern.compile("\\{[\\s\\S]*}");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** 通知邮件里常见的字段名（中英文）→ 字段 */
    private static final Map<String, Pattern> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put("name", label("buyer(?:'s)? name|buyer|contact(?: person| name)?|full name|your name|name|联系人|姓名|买家"));
        LABELS.put("company", label("company(?: name)?|organization|公司(?:名称)?|企业名称"));
        LABELS.put("country", label("country(?:/region)?|region|location|国家(?:/地区)?|地区"));
        LABELS.put("email", label("e-?mail(?: address)?|邮箱|电子邮件"));
        LABELS.put("phone", label("phone(?: number)?|tel(?:ephone)?|mobile|whatsapp|电话|手机"));
        LABELS.put("product", label("product(?: name)?|item|subject of inquiry|产品(?:名称)?|商品"));
        LABELS.put("quantity", label("(?:purchase )?quantity|qty|order quantity|采购量|数量"));
        LABELS.put("message", label("message|inquiry(?: content)?|requirements?|details|comments?|留言|询盘内容|需求"));
    }

    private static Pattern label(String names) {
        return Pattern.compile("(?im)^\\s*(?:" + names + ")\\s*[:：]\\s*(.+?)\\s*$");
    }

    /** 常见出口市场国家名 → ISO 二字码 */
    private static final Map<String, String> COUNTRIES = new HashMap<>();

    static {
        String[][] c = {{"united states", "US"}, {"usa", "US"}, {"america", "US"}, {"美国", "US"}, {"canada", "CA"}, {"加拿大", "CA"},
                {"mexico", "MX"}, {"墨西哥", "MX"}, {"brazil", "BR"}, {"巴西", "BR"}, {"united kingdom", "GB"}, {"uk", "GB"},
                {"england", "GB"}, {"英国", "GB"}, {"germany", "DE"}, {"德国", "DE"}, {"france", "FR"}, {"法国", "FR"},
                {"italy", "IT"}, {"意大利", "IT"}, {"spain", "ES"}, {"西班牙", "ES"}, {"netherlands", "NL"}, {"荷兰", "NL"},
                {"poland", "PL"}, {"波兰", "PL"}, {"russia", "RU"}, {"russian federation", "RU"}, {"俄罗斯", "RU"},
                {"turkey", "TR"}, {"türkiye", "TR"}, {"土耳其", "TR"}, {"united arab emirates", "AE"}, {"uae", "AE"}, {"阿联酋", "AE"},
                {"saudi arabia", "SA"}, {"沙特阿拉伯", "SA"}, {"沙特", "SA"}, {"india", "IN"}, {"印度", "IN"}, {"pakistan", "PK"},
                {"巴基斯坦", "PK"}, {"bangladesh", "BD"}, {"孟加拉", "BD"}, {"indonesia", "ID"}, {"印度尼西亚", "ID"}, {"印尼", "ID"},
                {"vietnam", "VN"}, {"viet nam", "VN"}, {"越南", "VN"}, {"thailand", "TH"}, {"泰国", "TH"}, {"malaysia", "MY"},
                {"马来西亚", "MY"}, {"philippines", "PH"}, {"菲律宾", "PH"}, {"singapore", "SG"}, {"新加坡", "SG"},
                {"japan", "JP"}, {"日本", "JP"}, {"south korea", "KR"}, {"korea", "KR"}, {"韩国", "KR"}, {"australia", "AU"},
                {"澳大利亚", "AU"}, {"new zealand", "NZ"}, {"新西兰", "NZ"}, {"south africa", "ZA"}, {"南非", "ZA"},
                {"nigeria", "NG"}, {"尼日利亚", "NG"}, {"kenya", "KE"}, {"肯尼亚", "KE"}, {"egypt", "EG"}, {"埃及", "EG"},
                {"chile", "CL"}, {"智利", "CL"}, {"colombia", "CO"}, {"哥伦比亚", "CO"}, {"peru", "PE"}, {"秘鲁", "PE"},
                {"argentina", "AR"}, {"阿根廷", "AR"}, {"israel", "IL"}, {"以色列", "IL"}, {"ukraine", "UA"}, {"乌克兰", "UA"}};
        for (String[] x : c) {
            COUNTRIES.put(x[0], x[1]);
        }
    }

    @Resource
    private ChannelLeadRuleMapper channelLeadRuleMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private CrmClueMapper crmClueMapper;
    @Resource
    private CrmPermissionService crmPermissionService;
    @Resource
    private ChannelNotifyService channelNotifyService;

    // ==================== 规则 ====================

    /** 本租户的规则；第一次使用时写入默认规则（阿里国际站、中国制造网） */
    public List<ChannelLeadRuleDO> rules() {
        List<ChannelLeadRuleDO> list = channelLeadRuleMapper.selectOrdered();
        if (list.isEmpty()) {
            channelLeadRuleMapper.insert(ChannelLeadRuleDO.builder().name("阿里国际站").matchField(ChannelLeadRuleDO.FIELD_FROM)
                    .keyword("alibaba.com").source(SOURCE_ALIBABA).enabled(true).sort(1).build());
            channelLeadRuleMapper.insert(ChannelLeadRuleDO.builder().name("中国制造网").matchField(ChannelLeadRuleDO.FIELD_FROM)
                    .keyword("made-in-china.com").source(SOURCE_MADE_IN_CHINA).enabled(true).sort(2).build());
            list = channelLeadRuleMapper.selectOrdered();
        }
        return list;
    }

    public Long saveRule(ChannelLeadRuleDO r) {
        if (StrUtil.isBlank(r.getName()) || StrUtil.isBlank(r.getKeyword()) || StrUtil.trim(r.getKeyword()).length() < 3) {
            throw exception(LEAD_RULE_INVALID, "请填写平台名称和至少 3 个字符的关键词");
        }
        r.setMatchField(ChannelLeadRuleDO.FIELD_SUBJECT.equals(r.getMatchField()) ? ChannelLeadRuleDO.FIELD_SUBJECT : ChannelLeadRuleDO.FIELD_FROM);
        r.setKeyword(StrUtil.trim(r.getKeyword()).toLowerCase(Locale.ROOT));
        r.setName(StrUtil.maxLength(StrUtil.trim(r.getName()), 50));
        r.setSource(r.getSource() == null ? SOURCE_WEBSITE_FORM : r.getSource());
        r.setEnabled(r.getEnabled() == null || r.getEnabled());
        r.setSort(r.getSort() == null ? 10 : r.getSort());
        if (r.getId() == null) {
            channelLeadRuleMapper.insert(r);
        } else {
            channelLeadRuleMapper.updateById(r);
        }
        return r.getId();
    }

    public void deleteRule(Long id) {
        channelLeadRuleMapper.deleteById(id);
    }

    ChannelLeadRuleDO match(ChannelMailMessageDO m) {
        String from = (StrUtil.nullToEmpty(m.getFromAddress()) + " " + StrUtil.nullToEmpty(m.getFromName())).toLowerCase(Locale.ROOT);
        String subject = StrUtil.nullToEmpty(m.getSubject()).toLowerCase(Locale.ROOT);
        for (ChannelLeadRuleDO r : rules()) {
            if (!Boolean.TRUE.equals(r.getEnabled()) || StrUtil.isBlank(r.getKeyword())) {
                continue;
            }
            String hay = ChannelLeadRuleDO.FIELD_SUBJECT.equals(r.getMatchField()) ? subject : from;
            if (hay.contains(r.getKeyword().toLowerCase(Locale.ROOT))) {
                return r;
            }
        }
        return null;
    }

    // ==================== 解析与建档 ====================

    /** 解析出的买家信息 */
    public record Lead(String name, String company, String countryCode, String email, String phone, String product,
                       String quantity, String message) {
    }

    /** 按字段名解析通知正文，例如「Country: Canada」「联系人：张三」 */
    static Lead parseLabels(String text, String ownEmail) {
        Map<String, String> f = new HashMap<>();
        for (Map.Entry<String, Pattern> e : LABELS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            if (m.find()) {
                f.put(e.getKey(), StrUtil.maxLength(m.group(1).trim(), 300));
            }
        }
        String email = f.get("email");
        if (email != null) {
            Matcher em = EMAIL.matcher(email);
            email = em.find() ? em.group().toLowerCase(Locale.ROOT) : null;
        }
        if (email != null && (email.equalsIgnoreCase(ownEmail) || email.matches("(?i).*(no-?reply|notification|service@|alibaba|made-in-china).*"))) {
            email = null;
        }
        String phone = f.get("phone");
        if (phone != null) {
            String d = phone.replaceAll("[^0-9+]", "");
            phone = d.replace("+", "").length() >= 7 ? (d.startsWith("+") ? d : "+" + d) : null;
        }
        return new Lead(f.get("name"), f.get("company"), country(f.get("country")), email, phone, f.get("product"),
                f.get("quantity"), f.get("message"));
    }

    static String country(String s) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        String t = s.trim();
        if (t.matches("(?i)[a-z]{2}")) {
            return t.toUpperCase(Locale.ROOT);
        }
        String k = t.toLowerCase(Locale.ROOT).replaceAll("[()（）].*", "").trim();
        return COUNTRIES.get(k);
    }

    private Lead llm(ChannelAiConfigDO cfg, String text, Long messageId, Long userId) {
        if (cfg == null || StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel()) || !channelCreditService.hasBalance()) {
            return null;
        }
        String system = "这是一封 B2B 平台或网站表单发来的询盘通知邮件，请提取买家信息。只输出一个 JSON 对象，字段："
                + "name（买家姓名）、company（公司）、country_code（ISO 3166-1 二字码）、email（买家邮箱，没有则 null，"
                + "不要填平台或我方邮箱）、phone（电话或 WhatsApp，含国家区号）、product（产品）、quantity（数量及单位）、"
                + "message（买家留言原文，最多 500 字）。无法确定的填 null，不要猜测。询盘通知解析，Return JSON only.";
        try {
            LlmClient.Result r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(),
                    List.of(LlmClient.Message.system(system), LlmClient.Message.user(StrUtil.maxLength(text, 5000))), 0);
            channelCreditService.consume("mail", messageId, userId, r.promptTokens() + r.completionTokens(), "平台询盘解析");
            Matcher mt = JSON_BLOCK.matcher(r.content());
            if (!mt.find()) {
                return null;
            }
            JsonNode j = JSON.readTree(mt.group());
            return new Lead(t(j, "name"), t(j, "company"), country(t(j, "country_code")), t(j, "email"), t(j, "phone"),
                    t(j, "product"), t(j, "quantity"), t(j, "message"));
        } catch (Exception e) {
            log.warn("[llm][邮件 {} 平台询盘解析失败] {}", messageId, e.getMessage());
            return null;
        }
    }

    private static String t(JsonNode j, String k) {
        JsonNode v = j.path(k);
        String s = v.isMissingNode() || v.isNull() ? null : v.asText().trim();
        return StrUtil.isBlank(s) || "null".equalsIgnoreCase(s) ? null : StrUtil.maxLength(s, 500);
    }

    private static String pick(String a, String b) {
        return StrUtil.isNotBlank(a) ? a : StrUtil.isNotBlank(b) ? b : null;
    }

    /**
     * 收到邮件时调用：是平台询盘通知则解析建线索并返回 true（调用方不再走普通线索建档和 AI 回复）。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean handle(Long messageId) {
        ChannelMailMessageDO m = channelMailMessageMapper.selectById(messageId);
        if (m == null || m.getDirection() != ChannelMailMessageDO.DIRECTION_IN) {
            return false;
        }
        ChannelLeadRuleDO rule = match(m);
        if (rule == null) {
            return false;
        }
        ChannelMailAccountDO account = channelMailAccountMapper.selectById(m.getAccountId());
        String text = StrUtil.isNotBlank(m.getTextBody()) ? m.getTextBody() : HtmlText.toText(m.getHtmlBody());
        text = "主题：" + StrUtil.nullToEmpty(m.getSubject()) + "\n" + StrUtil.nullToEmpty(text);
        Lead a = parseLabels(text, account == null ? null : account.getEmail());
        Lead b = llm(channelAiConfigMapper.selectCurrent(), text, m.getId(), account == null ? null : account.getUserId());
        Lead lead = b == null ? a : new Lead(pick(a.name(), b.name()), pick(a.company(), b.company()), pick(a.countryCode(), b.countryCode()),
                pick(a.email(), b.email()), pick(a.phone(), b.phone()), pick(a.product(), b.product()),
                pick(a.quantity(), b.quantity()), pick(a.message(), b.message()));

        // 去重：同一买家邮箱，或同一平台同名买家 30 天内已建过线索
        CrmClueDO exist = null;
        if (lead.email() != null) {
            exist = crmClueMapper.selectOne(new LambdaQueryWrapperX<CrmClueDO>().eq(CrmClueDO::getEmail, lead.email()).last("LIMIT 1"));
        } else if (lead.name() != null) {
            exist = crmClueMapper.selectOne(new LambdaQueryWrapperX<CrmClueDO>().eq(CrmClueDO::getName, lead.name())
                    .eq(CrmClueDO::getSource, rule.getSource()).ge(CrmClueDO::getCreateTime, LocalDateTime.now().minusDays(30))
                    .last("LIMIT 1"));
        }
        if (exist == null) {
            StringBuilder remark = new StringBuilder("由「").append(rule.getName()).append("」询盘通知自动建档");
            if (lead.product() != null || lead.quantity() != null) {
                remark.append("\n需求：").append(StrUtil.nullToEmpty(lead.product())).append(lead.quantity() == null ? "" : " × " + lead.quantity());
            }
            if (lead.message() != null) {
                remark.append("\n留言：").append(StrUtil.maxLength(lead.message(), 500));
            }
            if (lead.email() == null && lead.phone() == null) {
                remark.append("\n（平台未提供买家联系方式，请在平台后台回复）");
            }
            Long owner = account == null ? null : account.getUserId();
            CrmClueDO clue = CrmClueDO.builder()
                    .name(StrUtil.maxLength(StrUtil.blankToDefault(pick(lead.name(), lead.company()), rule.getName() + "询盘"), 100))
                    .companyName(lead.company()).countryCode(lead.countryCode()).email(lead.email()).whatsapp(lead.phone())
                    .mobile(lead.phone()).source(rule.getSource()).ownerUserId(owner)
                    .contactLastTime(m.getSentTime()).contactLastContent(StrUtil.maxLength(StrUtil.nullToEmpty(m.getSubject()), 500))
                    .remark(StrUtil.maxLength(remark.toString(), 1000)).build();
            clue.setFollowUpStatus(false);
            clue.setTransformStatus(false);
            clue.setAiHosted(true);
            crmClueMapper.insert(clue);
            if (owner != null) {
                crmPermissionService.createPermission(new CrmPermissionCreateReqBO().setBizType(CrmBizTypeEnum.CRM_CLUE.getType())
                        .setBizId(clue.getId()).setUserId(owner).setLevel(CrmPermissionLevelEnum.OWNER.getLevel()));
            }
            channelNotifyService.onNewLead(clue.getId(), clue.getName(), owner, rule.getName());
            exist = clue;
            log.info("[handle][邮件 {} 识别为「{}」询盘，建线索 {}]", m.getId(), rule.getName(), clue.getId());
        }
        channelMailMessageMapper.updateById(new ChannelMailMessageDO().setId(m.getId()).setClueId(exist.getId()));
        return true;
    }

}
