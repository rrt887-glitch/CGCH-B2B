package cn.iocoder.yudao.module.channel.service.cases;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Scope;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiCaseDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiCaseMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.ai.AiPromptBuilder;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.ai.VectorMath;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardService;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 成交案例：AI 读取已成交客户的完整往来，总结成交关键点、客户顾虑及回应、有效话术；
 * 人工确认后开启，AI 回复同类询盘时参考（去掉原客户名称与价格）。
 */
@Service
@Slf4j
public class ChannelAiCaseService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 回复时最多参考几个案例 */
    private static final int MAX_CASES = 2;
    private static final double SEMANTIC_MIN = 0.35;
    private static final Pattern PRICE = Pattern.compile("(?i)(US\\$|USD|EUR|€|\\$|¥|RMB|CNY)\\s?\\d[\\d,.]*|\\d[\\d,.]*\\s?(USD|EUR|RMB|CNY|美元|欧元|元)");

    @Resource
    private ChannelAiCaseMapper channelAiCaseMapper;
    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private CrmCustomerMapper crmCustomerMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private ChannelDashboardService channelDashboardService;

    private ChannelAiConfigDO config() {
        ChannelAiConfigDO c = channelAiConfigMapper.selectCurrent();
        return c != null ? c : new ChannelAiConfigDO();
    }

    // ==================== 候选与生成 ====================

    /** 已成交、可见范围内的客户，以及是否已有案例 */
    public List<Map<String, Object>> candidates(Long loginUserId) {
        Scope s = channelDashboardService.resolveScope(loginUserId, null);
        LambdaQueryWrapperX<CrmCustomerDO> q = new LambdaQueryWrapperX<CrmCustomerDO>().eq(CrmCustomerDO::getDealStatus, true);
        if (!s.isAll()) {
            q.in(CrmCustomerDO::getOwnerUserId, s.getUserIds().isEmpty() ? List.of(-1L) : s.getUserIds());
        }
        Set<Long> done = channelAiCaseMapper.selectList().stream().map(ChannelAiCaseDO::getCustomerId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (CrmCustomerDO c : crmCustomerMapper.selectList(q.orderByDesc(CrmCustomerDO::getUpdateTime).last("LIMIT 200"))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("companyName", c.getCompanyName());
            m.put("countryCode", c.getCountryCode());
            m.put("ownerUserId", c.getOwnerUserId());
            m.put("hasCase", done.contains(c.getId()));
            out.add(m);
        }
        return out;
    }

    /** 根据客户的完整往来生成案例（默认不启用，等人工确认），返回编号 */
    public Long generate(Long userId, Long customerId) {
        CrmCustomerDO c = crmCustomerMapper.selectById(customerId);
        if (c == null) {
            throw exception(CASE_INVALID, "客户不存在");
        }
        Scope s = channelDashboardService.resolveScope(userId, null);
        if (!s.isAll() && !s.getUserIds().contains(c.getOwnerUserId())) {
            throw exception(DASHBOARD_USER_FORBIDDEN);
        }
        List<String> history = history(c.getId());
        if (history.size() < 2) {
            throw exception(CASE_INVALID, "与该客户的往来记录太少，无法总结成交经验");
        }
        ChannelAiConfigDO cfg = config();
        if (StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
            throw exception(CASE_INVALID, "请先在数字员工设置中配置大模型");
        }
        if (!channelCreditService.hasBalance()) {
            throw exception(AI_CREDIT_INSUFFICIENT);
        }
        LlmClient.Result r;
        try {
            r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(), List.of(
                    LlmClient.Message.system(generatePrompt()),
                    LlmClient.Message.user("客户：" + c.getName() + (StrUtil.isBlank(c.getCompanyName()) ? "" : "（" + c.getCompanyName() + "）")
                            + "\n国家：" + StrUtil.nullToEmpty(c.getCountryCode()) + "\n\n## 往来记录（按时间先后）\n" + String.join("\n", history))), 0.3);
        } catch (LlmClient.LlmException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
        JsonNode j = parse(r.content());
        List<String> names = identifiers(c);
        ChannelAiCaseDO k = ChannelAiCaseDO.builder()
                .title(StrUtil.maxLength(anonymize(text(j, "title", "成交案例"), names), 200))
                .customerId(c.getId()).customerName(c.getName())
                .industry(StrUtil.maxLength(anonymize(text(j, "industry", ""), names), 200))
                .product(StrUtil.maxLength(anonymize(text(j, "product", ""), names), 200))
                .countryCode(StrUtil.blankToDefault(c.getCountryCode(), text(j, "countryCode", "")))
                .summary(anonymize(text(j, "summary", ""), names))
                .keyPoints(JsonUtils.toJsonString(list(j, "keyPoints", names)))
                .objections(JsonUtils.toJsonString(list(j, "objections", names)))
                .phrases(JsonUtils.toJsonString(list(j, "phrases", names)))
                .enabled(false).source(ChannelAiCaseDO.SOURCE_AI).userId(userId).build();
        channelAiCaseMapper.insert(k);
        long credits = channelCreditService.consume("case", k.getId(), userId, r.promptTokens() + r.completionTokens(), "成交案例");
        channelAiCaseMapper.updateById(new ChannelAiCaseDO().setId(k.getId()).setCredits(credits));
        return k.getId();
    }

    private List<String> history(Long customerId) {
        List<String> out = new ArrayList<>();
        List<ChannelMailMessageDO> mails = channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .eq(ChannelMailMessageDO::getCustomerId, customerId).ne(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_DRAFT)
                .orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 30"));
        Collections.reverse(mails);
        for (ChannelMailMessageDO m : mails) {
            out.add("[" + (m.getSentTime() == null ? "" : m.getSentTime().format(DAY)) + " 邮件 "
                    + (m.getDirection() == ChannelMailMessageDO.DIRECTION_IN ? "客户" : "我方") + "] " + StrUtil.nullToEmpty(m.getSubject())
                    + "：" + HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), 600));
        }
        for (ChannelWaConversationDO conv : channelWaConversationMapper.selectList(new LambdaQueryWrapperX<ChannelWaConversationDO>()
                .eq(ChannelWaConversationDO::getCustomerId, customerId).last("LIMIT 3"))) {
            List<ChannelWaMessageDO> msgs = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .eq(ChannelWaMessageDO::getConversationId, conv.getId()).orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 40"));
            Collections.reverse(msgs);
            for (ChannelWaMessageDO m : msgs) {
                if (StrUtil.isNotBlank(m.getTextBody())) {
                    out.add("[" + (m.getMessageTime() == null ? "" : m.getMessageTime().format(DAY)) + " WhatsApp "
                            + (m.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "客户" : "我方") + "] " + StrUtil.maxLength(m.getTextBody(), 400));
                }
            }
        }
        return out;
    }

    private static String generatePrompt() {
        return "你是外贸销售教练，要把一笔已成交订单的沟通过程整理成「成交案例」，供销售团队和 AI 回复同类询盘时借鉴。\n"
                + "规则：\n1. 只依据提供的往来记录，不要编造。\n"
                + "2. 案例要能复用：用「客户」代称，不要写客户姓名、公司名、邮箱电话，也不要写具体成交价格和订单金额。\n"
                + "3. 用简体中文；话术（phrases）保留往来中实际使用的语言。\n"
                + "4. 只输出 JSON：{\"title\": \"一句话标题\", \"industry\": \"客户行业或类型\", \"product\": \"成交产品\", "
                + "\"summary\": \"2~4 句背景与过程\", \"keyPoints\": [\"成交关键点\"], \"objections\": [\"顾虑：…；回应：…\"], "
                + "\"phrases\": [\"有效话术原文\"]}";
    }

    private static JsonNode parse(String content) {
        String s = StrUtil.nullToEmpty(content);
        int i = s.indexOf('{'), j = s.lastIndexOf('}');
        if (i >= 0 && j > i) {
            try {
                return JSON.readTree(s.substring(i, j + 1));
            } catch (Exception ignored) {
                // 落到下面
            }
        }
        return JSON.createObjectNode().put("summary", s.trim());
    }

    private static String text(JsonNode j, String k, String def) {
        JsonNode v = j.path(k);
        return v.isTextual() && StrUtil.isNotBlank(v.asText()) ? v.asText().trim() : def;
    }

    private static List<String> list(JsonNode j, String k, List<String> names) {
        List<String> out = new ArrayList<>();
        j.path(k).forEach(x -> {
            String s = x.isTextual() ? x.asText() : x.toString();
            if (StrUtil.isNotBlank(s)) {
                out.add(StrUtil.maxLength(anonymize(s.trim(), names), 500));
            }
        });
        return out.size() > 10 ? out.subList(0, 10) : out;
    }

    /** 客户的名称、公司名、邮箱等可识别信息 */
    private static List<String> identifiers(CrmCustomerDO c) {
        List<String> l = new ArrayList<>();
        for (String s : new String[]{c.getName(), c.getCompanyName(), c.getEmail(), c.getWhatsapp()}) {
            if (StrUtil.isNotBlank(s) && s.trim().length() >= 3) {
                l.add(s.trim());
            }
        }
        l.sort((a, b) -> b.length() - a.length());
        return l;
    }

    /** 去掉原客户的可识别信息和具体金额 */
    static String anonymize(String text, List<String> names) {
        String s = StrUtil.nullToEmpty(text);
        for (String n : names) {
            s = Pattern.compile(Pattern.quote(n), Pattern.CASE_INSENSITIVE).matcher(s).replaceAll("客户");
        }
        return PRICE.matcher(s).replaceAll("[价格]");
    }

    // ==================== 维护 ====================

    public List<ChannelAiCaseDO> list() {
        return channelAiCaseMapper.selectListOrdered();
    }

    public ChannelAiCaseDO get(Long id) {
        ChannelAiCaseDO k = channelAiCaseMapper.selectById(id);
        if (k == null) {
            throw exception(CASE_NOT_EXISTS);
        }
        return k;
    }

    /** 新增或修改；内容变化后重建向量 */
    public Long save(Long userId, ChannelAiCaseDO req, List<String> keyPoints, List<String> objections, List<String> phrases) {
        if (StrUtil.isBlank(req.getTitle())) {
            throw exception(CASE_INVALID, "请填写案例标题");
        }
        List<String> names = new ArrayList<>();
        if (req.getId() != null) {
            ChannelAiCaseDO old = get(req.getId());
            if (old.getCustomerId() != null) {
                CrmCustomerDO c = crmCustomerMapper.selectById(old.getCustomerId());
                if (c != null) {
                    names = identifiers(c);
                }
            }
        }
        final List<String> ids = names;
        java.util.function.Function<List<String>, String> clean = l -> JsonUtils.toJsonString(
                (l == null ? List.<String>of() : l).stream().filter(StrUtil::isNotBlank).map(x -> anonymize(x.trim(), ids)).limit(10).toList());
        ChannelAiCaseDO k = new ChannelAiCaseDO().setTitle(StrUtil.maxLength(req.getTitle().trim(), 200))
                .setIndustry(StrUtil.maxLength(StrUtil.nullToEmpty(req.getIndustry()), 200))
                .setProduct(StrUtil.maxLength(StrUtil.nullToEmpty(req.getProduct()), 200))
                .setCountryCode(StrUtil.nullToEmpty(req.getCountryCode())).setSummary(anonymize(req.getSummary(), ids))
                .setKeyPoints(clean.apply(keyPoints)).setObjections(clean.apply(objections)).setPhrases(clean.apply(phrases))
                .setEnabled(Boolean.TRUE.equals(req.getEnabled())).setEmbedding("").setEmbeddingModel("");
        if (req.getId() == null) {
            k.setSource(ChannelAiCaseDO.SOURCE_MANUAL).setUserId(userId).setCredits(0L);
            channelAiCaseMapper.insert(k);
        } else {
            k.setId(req.getId());
            channelAiCaseMapper.updateById(k);
        }
        if (Boolean.TRUE.equals(k.getEnabled())) {
            embed(get(k.getId()), userId);
        }
        return k.getId();
    }

    public void setEnabled(Long userId, Long id, boolean enabled) {
        ChannelAiCaseDO k = get(id);
        channelAiCaseMapper.updateById(new ChannelAiCaseDO().setId(id).setEnabled(enabled));
        if (enabled && StrUtil.isBlank(k.getEmbedding())) {
            embed(k, userId);
        }
    }

    public void delete(Long id) {
        get(id);
        channelAiCaseMapper.deleteById(id);
    }

    private static String caseText(ChannelAiCaseDO k) {
        return String.join("\n", StrUtil.nullToEmpty(k.getTitle()), StrUtil.nullToEmpty(k.getIndustry()), StrUtil.nullToEmpty(k.getProduct()),
                StrUtil.nullToEmpty(k.getSummary()), String.join("；", arr(k.getKeyPoints())), String.join("；", arr(k.getObjections())));
    }

    /** 配置了向量模型时为案例建向量（失败只记日志，按关键词匹配） */
    private void embed(ChannelAiCaseDO k, Long userId) {
        ChannelAiConfigDO cfg = config();
        if (StrUtil.hasBlank(cfg.getEmbeddingModel(), cfg.getLlmBaseUrl(), cfg.getLlmApiKey()) || !channelCreditService.hasBalance()) {
            return;
        }
        try {
            LlmClient.Embeddings e = LlmClient.embed(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getEmbeddingModel(),
                    List.of(StrUtil.maxLength(caseText(k), 4000)));
            channelAiCaseMapper.updateById(new ChannelAiCaseDO().setId(k.getId())
                    .setEmbedding(VectorMath.toJson(e.vectors().get(0))).setEmbeddingModel(cfg.getEmbeddingModel()));
            channelCreditService.consume("case", k.getId(), userId, Math.max(e.tokens(), 1), "成交案例向量化");
        } catch (Exception ex) {
            log.warn("[embed][案例({}) 生成向量失败] {}", k.getId(), ex.getMessage());
        }
    }

    public static List<String> arr(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> l = JsonUtils.parseArray(json, String.class);
            return l == null ? List.of() : l;
        } catch (Exception e) {
            return List.of();
        }
    }

    // ==================== 回复时参考 ====================

    /** 选出与客户消息最相关的已启用案例，生成提示词片段；没有相关案例时返回空串 */
    public String promptBlock(String customerText, float[] queryVector) {
        if (StrUtil.isBlank(customerText)) {
            return "";
        }
        List<ChannelAiCaseDO> all = channelAiCaseMapper.selectEnabled();
        if (all.isEmpty()) {
            return "";
        }
        String model = config().getEmbeddingModel();
        Set<String> terms = AiPromptBuilder.terms(customerText);
        List<Map.Entry<ChannelAiCaseDO, Double>> scored = new ArrayList<>();
        for (ChannelAiCaseDO k : all) {
            Set<String> words = AiPromptBuilder.terms(caseText(k));
            double score = terms.stream().filter(words::contains).count();
            if (queryVector != null && StrUtil.isNotBlank(k.getEmbedding()) && Objects.equals(model, k.getEmbeddingModel())) {
                double cos = VectorMath.cosine(queryVector, VectorMath.parse(k.getEmbedding()));
                score += cos >= SEMANTIC_MIN ? cos * 10 : 0;
            }
            if (score >= 2) { // 至少两个词重合或语义相近，避免牵强的参考
                scored.add(Map.entry(k, score));
            }
        }
        if (scored.isEmpty()) {
            return "";
        }
        scored.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder sb = new StringBuilder("\n\n## 成交经验参考\n以下来自其他已成交客户的经验，只借鉴思路和话术；"
                + "不要提及任何其他客户的名称、成交价格或订单细节，也不要说「其他客户」如何如何。\n");
        for (Map.Entry<ChannelAiCaseDO, Double> e : scored.subList(0, Math.min(MAX_CASES, scored.size()))) {
            ChannelAiCaseDO k = e.getKey();
            sb.append("### ").append(StrUtil.blankToDefault(k.getIndustry(), "同类客户")).append(" · ")
                    .append(StrUtil.blankToDefault(k.getProduct(), k.getTitle())).append("\n");
            List<String> kp = arr(k.getKeyPoints()), ob = arr(k.getObjections()), ph = arr(k.getPhrases());
            if (!kp.isEmpty()) {
                sb.append("成交关键点：").append(String.join("；", kp)).append("\n");
            }
            if (!ob.isEmpty()) {
                sb.append("客户顾虑与回应：").append(String.join("；", ob)).append("\n");
            }
            if (!ph.isEmpty()) {
                sb.append("有效话术：").append(String.join(" / ", ph)).append("\n");
            }
        }
        return sb.toString();
    }

}
