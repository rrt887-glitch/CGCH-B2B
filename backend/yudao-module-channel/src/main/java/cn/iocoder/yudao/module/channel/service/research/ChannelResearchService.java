package cn.iocoder.yudao.module.channel.service.research;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.research.ChannelResearchDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiKnowledgeMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.research.ChannelResearchMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.ai.WebSearchClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.service.lead.LeadInference;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 深度背调：用后台配置的大模型给客户做背调。资料来源：
 * 客户官网的公开页面、我方与客户的往来记录、联网搜索的公开结果（在智能体里配置了联网搜索时），
 * 以及大模型自身掌握的公开知识（单独列出、标注需核实）。
 * 报告包括企业概况、客户类型（贸易商 / 终端工厂）、采购情况、规模、痛点、推荐我方产品、报价与付款建议、风险提示，
 * 可一键补充到 CRM 客户 / 线索。只收集企业层面的公开商业信息。
 */
@Service
@Slf4j
public class ChannelResearchService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int SNIPPET = 240;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "channel-research");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private ChannelResearchMapper channelResearchMapper;
    @Resource
    private CrmCustomerMapper crmCustomerMapper;
    @Resource
    private CrmClueMapper crmClueMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelAiKnowledgeMapper channelAiKnowledgeMapper;
    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private ChannelCreditService channelCreditService;

    /** 允许访问的内网主机，逗号分隔（仅自动化测试使用，生产请保持为空） */
    @Value("${cgch.research.allowed-private-hosts:}")
    private String allowedPrivateHosts;
    private WebsiteFetcher fetcher;

    @PostConstruct
    public void init() {
        fetcher = new WebsiteFetcher(StrUtil.split(allowedPrivateHosts, ',', true, true));
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    // ==================== 发起 ====================

    /** 发起背调：官网、客户、线索至少提供一个；后台执行，返回编号 */
    public Long create(Long userId, String website, String companyName, Long customerId, Long clueId) {
        CrmCustomerDO customer = customerId == null ? null : crmCustomerMapper.selectById(customerId);
        CrmClueDO clue = clueId == null ? null : crmClueMapper.selectById(clueId);
        if (customerId != null && customer == null) {
            throw exception(RESEARCH_INVALID, "客户不存在");
        }
        if (clueId != null && clue == null) {
            throw exception(RESEARCH_INVALID, "线索不存在");
        }
        String site = StrUtil.trimToNull(website);
        if (site == null && customer != null) {
            site = StrUtil.blankToDefault(StrUtil.trimToNull(customer.getWebsite()), LeadInference.websiteFromEmail(customer.getEmail()));
        }
        if (site == null && clue != null) {
            site = StrUtil.blankToDefault(StrUtil.trimToNull(clue.getWebsite()), LeadInference.websiteFromEmail(clue.getEmail()));
        }
        if (site == null && customer == null && clue == null && StrUtil.isBlank(companyName)) {
            throw exception(RESEARCH_INVALID, "请填写官网或公司名称，或选择一个客户 / 线索");
        }
        if (site != null) {
            try {
                site = WebsiteFetcher.normalize(site).toString();
            } catch (WebsiteFetcher.FetchException e) {
                throw exception(RESEARCH_INVALID, e.getMessage());
            }
        }
        String name = StrUtil.blankToDefault(StrUtil.trimToNull(companyName),
                customer != null ? StrUtil.blankToDefault(customer.getCompanyName(), customer.getName())
                        : clue != null ? StrUtil.blankToDefault(clue.getCompanyName(), clue.getName()) : null);
        ChannelResearchDO r = ChannelResearchDO.builder().companyName(StrUtil.maxLength(name, 200)).website(site)
                .customerId(customerId).clueId(clueId).status(ChannelResearchDO.STATUS_RUNNING).credits(0L).userId(userId).build();
        channelResearchMapper.insert(r);
        Long tenantId = TenantContextHolder.getTenantId();
        executor.submit(() -> {
            try {
                TenantUtils.execute(tenantId, () -> run(r.getId()));
            } catch (Exception e) {
                log.warn("[create][背调({}) 执行失败] {}", r.getId(), e.getMessage());
            }
        });
        return r.getId();
    }

    private void fail(Long id, String reason) {
        channelResearchMapper.updateById(new ChannelResearchDO().setId(id).setStatus(ChannelResearchDO.STATUS_FAILED)
                .setReasonDetail(StrUtil.maxLength(reason, 250)));
    }

    /**
     * 同步为客户做一次背调（客户唤醒写信前调用），返回完成或失败的记录。
     * 官网取客户资料里的官网或企业邮箱域名，没有时只靠往来记录、联网搜索和大模型知识。
     */
    public ChannelResearchDO runForCustomer(Long userId, Long customerId) {
        CrmCustomerDO c = crmCustomerMapper.selectById(customerId);
        if (c == null) {
            return null;
        }
        String site = StrUtil.blankToDefault(StrUtil.trimToNull(c.getWebsite()), LeadInference.websiteFromEmail(c.getEmail()));
        if (site != null) {
            try {
                site = WebsiteFetcher.normalize(site).toString();
            } catch (WebsiteFetcher.FetchException e) {
                site = null;
            }
        }
        ChannelResearchDO r = ChannelResearchDO.builder()
                .companyName(StrUtil.maxLength(StrUtil.blankToDefault(c.getCompanyName(), c.getName()), 200)).website(site)
                .customerId(customerId).status(ChannelResearchDO.STATUS_RUNNING).credits(0L).userId(userId).build();
        channelResearchMapper.insert(r);
        try {
            run(r.getId());
        } catch (Exception e) {
            fail(r.getId(), "背调失败：" + e.getMessage());
        }
        return channelResearchMapper.selectById(r.getId());
    }

    /** 客户最近一次完成的背调；maxAgeDays 天以前的不算 */
    public ChannelResearchDO latestDone(Long customerId, int maxAgeDays) {
        if (customerId == null) {
            return null;
        }
        return channelResearchMapper.selectOne(new LambdaQueryWrapperX<ChannelResearchDO>()
                .eq(ChannelResearchDO::getCustomerId, customerId).eq(ChannelResearchDO::getStatus, ChannelResearchDO.STATUS_DONE)
                .ge(ChannelResearchDO::getCreateTime, java.time.LocalDateTime.now().minusDays(maxAgeDays))
                .orderByDesc(ChannelResearchDO::getId).last("LIMIT 1"));
    }

    /** 与客户的最近往来摘要（邮件、WhatsApp） */
    public List<String> recentHistory(Long customerId) {
        return customerId == null ? List.of() : history(new ChannelResearchDO().setCustomerId(customerId));
    }

    private void run(Long id) {
        ChannelResearchDO r = channelResearchMapper.selectById(id);
        ChannelAiConfigDO cfg = channelAiService.getConfig();
        if (StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
            fail(id, "请先在数字员工设置中配置大模型");
            return;
        }
        if (!channelCreditService.hasBalance()) {
            fail(id, "智点余额不足");
            return;
        }
        // 1. 官网公开页面
        List<WebsiteFetcher.Page> pages = new ArrayList<>();
        String siteNote = null;
        if (r.getWebsite() != null) {
            try {
                pages = fetcher.fetchSiteWithFallback(URI.create(r.getWebsite()));
                if (!pages.isEmpty()) { // 记下实际可访问的地址（可能已从 https 改为 http）
                    URI actual = URI.create(pages.get(0).url());
                    String root = actual.getScheme() + "://" + actual.getHost() + (actual.getPort() > 0 ? ":" + actual.getPort() : "") + "/";
                    if (!root.equals(r.getWebsite())) {
                        r.setWebsite(root);
                        channelResearchMapper.updateById(new ChannelResearchDO().setId(id).setWebsite(root));
                    }
                }
            } catch (WebsiteFetcher.FetchException e) {
                siteNote = e.getMessage();
            }
        }
        // 2. 我方往来记录
        List<String> history = history(r);
        // 3. 联网搜索公开信息（配置了联网搜索时）：企业概况、采购与进口记录、规模
        List<WebSearchClient.Hit> hits = search(cfg, r);
        if (pages.isEmpty() && history.isEmpty() && hits.isEmpty() && StrUtil.isBlank(r.getCompanyName())) {
            fail(id, StrUtil.blankToDefault(siteNote, "没有可用的资料：官网无法访问，也没有往来记录"));
            return;
        }
        // 4. 大模型整理（资料不足时可补充自身掌握的公开知识，单独标注）
        try {
            LlmClient.Result res = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(), List.of(
                    LlmClient.Message.system(systemPrompt()), LlmClient.Message.user(userPrompt(r, pages, history, hits, siteNote))), 0.2);
            JsonNode report = parseReport(res.content());
            long credits = channelCreditService.consume("research", id, r.getUserId(), res.promptTokens() + res.completionTokens(), "深度背调");
            List<Map<String, String>> sources = new ArrayList<>();
            pages.forEach(p -> sources.add(Map.of("url", p.url(), "title", StrUtil.nullToEmpty(p.title()),
                    "snippet", StrUtil.maxLength(p.text(), SNIPPET), "type", "website")));
            Set<String> seen = sources.stream().map(x -> x.get("url")).collect(Collectors.toSet());
            hits.stream().filter(h -> seen.add(h.url())).forEach(h -> sources.add(Map.of("url", h.url(), "title", StrUtil.nullToEmpty(h.title()),
                    "snippet", StrUtil.maxLength(h.content(), SNIPPET), "type", "search")));
            String name = StrUtil.blankToDefault(report.path("companyName").asText(null), r.getCompanyName());
            channelResearchMapper.updateById(new ChannelResearchDO().setId(id).setStatus(ChannelResearchDO.STATUS_DONE)
                    .setReport(report.toString()).setSources(JsonUtils.toJsonString(sources)).setCredits(credits)
                    .setCompanyName(StrUtil.maxLength(name, 200)).setReasonDetail(StrUtil.nullToEmpty(siteNote)));
        } catch (Exception e) {
            fail(id, "AI 整理失败：" + e.getMessage());
        }
    }

    /** 联网搜索：按公司名（没有时用官网域名）查概况、采购 / 进口记录、规模，最多 3 次，去重后最多 12 条 */
    private List<WebSearchClient.Hit> search(ChannelAiConfigDO cfg, ChannelResearchDO r) {
        if (StrUtil.isBlank(cfg.getSearchApiKey())) {
            return List.of();
        }
        String host = null;
        if (r.getWebsite() != null) {
            try {
                host = StrUtil.removePrefix(URI.create(r.getWebsite()).getHost(), "www.");
            } catch (Exception ignored) {
                // 忽略
            }
        }
        String who = StrUtil.isNotBlank(r.getCompanyName()) ? "\"" + r.getCompanyName() + "\"" : host;
        if (StrUtil.isBlank(who)) {
            return List.of();
        }
        String site = host != null && StrUtil.isNotBlank(r.getCompanyName()) ? " " + host : "";
        List<String> queries = List.of(who + site + " company profile products",
                who + " importer shipments suppliers import records",
                who + " employees revenue company size");
        Map<String, WebSearchClient.Hit> out = new LinkedHashMap<>();
        for (String q : queries) {
            for (WebSearchClient.Hit h : WebSearchClient.search(cfg.getSearchBaseUrl(), cfg.getSearchApiKey(), q)) {
                if (out.size() < 12) {
                    out.putIfAbsent(StrUtil.blankToDefault(h.url(), h.title()), h);
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    /** 与该客户 / 线索的最近往来（邮件和 WhatsApp 各取最近几条摘要） */
    private List<String> history(ChannelResearchDO r) {
        List<String> out = new ArrayList<>();
        if (r.getCustomerId() == null && r.getClueId() == null) {
            return out;
        }
        LambdaQueryWrapperX<ChannelMailMessageDO> mq = new LambdaQueryWrapperX<>();
        mq.eqIfPresent(ChannelMailMessageDO::getCustomerId, r.getCustomerId());
        if (r.getCustomerId() == null) {
            mq.eq(ChannelMailMessageDO::getClueId, r.getClueId());
        }
        mq.ne(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_DRAFT).orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 8");
        for (ChannelMailMessageDO m : channelMailMessageMapper.selectList(mq)) {
            out.add((m.getDirection() == ChannelMailMessageDO.DIRECTION_IN ? "客户来信" : "我方发出") + "（邮件 "
                    + (m.getSentTime() == null ? "" : m.getSentTime().format(DAY)) + "）主题：" + StrUtil.nullToEmpty(m.getSubject())
                    + "；" + HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), 300));
        }
        LambdaQueryWrapperX<ChannelWaConversationDO> cq = new LambdaQueryWrapperX<>();
        cq.eqIfPresent(ChannelWaConversationDO::getCustomerId, r.getCustomerId());
        if (r.getCustomerId() == null) {
            cq.eq(ChannelWaConversationDO::getClueId, r.getClueId());
        }
        for (ChannelWaConversationDO c : channelWaConversationMapper.selectList(cq.last("LIMIT 3"))) {
            List<ChannelWaMessageDO> msgs = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .eq(ChannelWaMessageDO::getConversationId, c.getId()).orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 8"));
            Collections.reverse(msgs);
            for (ChannelWaMessageDO m : msgs) {
                if (StrUtil.isNotBlank(m.getTextBody())) {
                    out.add((m.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "客户" : "我方") + "（WhatsApp "
                            + (m.getMessageTime() == null ? "" : m.getMessageTime().format(DAY)) + "）：" + StrUtil.maxLength(m.getTextBody(), 300));
                }
            }
        }
        return out;
    }

    private static String systemPrompt() {
        return "你是外贸企业的客户背调分析师。根据提供的「客户官网公开页面」「联网搜索结果」和「我方与客户的往来记录」，"
                + "为业务员整理一份客户背调报告：判断对方是谁（贸易商还是终端工厂）、最近在哪采购、买多少、规模和付款信用如何，"
                + "以及该给什么价、要不要预付款、推荐我方哪些产品。\n"
                + "规则：\n1. 事实只能来自提供的资料；资料里没有的写 null 或空数组，不要编造（尤其是规模、营收、成立时间、采购量）。\n"
                + "2. 如果你本身了解这家公司的公开信息，可以写进 modelKnowledge，每条注明「需核实」，不要混进其他字段。\n"
                + "3. 判断类字段（buyerKind、painPoints、quoteAdvice、paymentAdvice、recommendedProducts）是你的分析，要写出依据；依据不足时明确说明。\n"
                + "4. 只整理企业层面的商业信息；联系方式只记录官网公开的公司邮箱、电话、地址，不记录员工个人信息。\n"
                + "5. 用简体中文撰写，公司名、产品名保留原文。\n"
                + "6. 只输出 JSON，字段：companyName, countryCode（ISO 二字码）, summary（2~4 句概况）, "
                + "businessType（manufacturer/brand/distributor/wholesaler/retailer/e-commerce/trading/other 之一）, "
                + "buyerKind（{\"kind\": \"trader\" 贸易商/转卖 | \"end_user\" 终端工厂/自用 | \"unknown\", \"reason\": 依据}）, "
                + "mainProducts（数组）, markets（数组）, scale（规模线索，原文依据）, certifications（数组）, "
                + "companyContacts（数组，官网公开的公司联系方式）, "
                + "purchasing（{\"suppliers\": 最近的供应商或采购来源数组, \"products\": 采购的品类数组, \"volume\": 采购量或频率线索, \"basis\": 依据}）, "
                + "creditSignals（数组：付款能力与信用线索，如成立年限、规模、负面信息）, "
                + "purchasingSignals（数组：他们可能向我们采购什么、依据是什么）, painPoints（数组：对方可能的痛点，如交期、价格、新品、认证）, "
                + "recommendedProducts（数组：[{\"name\": 我方产品名, \"reason\": 推荐理由}]，只能从「我方企业与产品」里挑）, "
                + "quoteAdvice（报价策略建议）, paymentAdvice（付款方式建议，如是否要求预付款、比例）, "
                + "talkingPoints（数组：业务员跟进时的切入点）, risks（数组：需要注意的风险或疑点）, "
                + "modelKnowledge（数组：你自身了解的公开信息，每条注明需核实）, confidence（high/medium/low）。";
    }

    private String userPrompt(ChannelResearchDO r, List<WebsiteFetcher.Page> pages, List<String> history,
                              List<WebSearchClient.Hit> hits, String siteNote) {
        StringBuilder sb = new StringBuilder();
        sb.append("背调对象：").append(StrUtil.blankToDefault(r.getCompanyName(), "（未知）"))
                .append("；官网：").append(StrUtil.blankToDefault(r.getWebsite(), "（无）")).append("\n");
        if (siteNote != null) {
            sb.append("官网访问情况：").append(siteNote).append("\n");
        }
        String ours = channelAiKnowledgeMapper.selectEnabled().stream()
                .filter(k -> !ChannelAiKnowledgeDO.TYPE_FAQ.equals(k.getType()))
                .map(k -> StrUtil.nullToEmpty(k.getTitle()) + "：" + StrUtil.maxLength(k.getContent(), 300))
                .collect(Collectors.joining("\n"));
        if (StrUtil.isNotBlank(ours)) {
            sb.append("\n## 我方企业与产品（用于判断采购信号、推荐产品）\n").append(StrUtil.maxLength(ours, 3000)).append("\n");
        }
        if (!pages.isEmpty()) {
            sb.append("\n## 客户官网公开页面\n");
            for (WebsiteFetcher.Page p : pages) {
                sb.append("### ").append(p.title()).append("（").append(p.url()).append("）\n").append(p.text()).append("\n\n");
            }
        }
        if (!hits.isEmpty()) {
            sb.append("\n## 联网搜索结果（公开网页摘要，可能包含同名公司，注意甄别）\n");
            for (WebSearchClient.Hit h : hits) {
                sb.append("- ").append(h.title()).append("（").append(h.url()).append("）：").append(h.content()).append("\n");
            }
        }
        if (!history.isEmpty()) {
            sb.append("\n## 我方与客户的往来记录（最近）\n").append(String.join("\n", history)).append("\n");
        }
        if (pages.isEmpty() && hits.isEmpty() && history.isEmpty()) {
            sb.append("\n（没有官网、搜索结果和往来记录，只能依据你自身了解的公开信息，请全部写进 modelKnowledge 并注明需核实，其他事实字段留空。）\n");
        }
        return sb.toString();
    }

    static JsonNode parseReport(String content) {
        String s = StrUtil.nullToEmpty(content);
        int i = s.indexOf('{'), j = s.lastIndexOf('}');
        if (i >= 0 && j > i) {
            try {
                return JSON.readTree(s.substring(i, j + 1));
            } catch (Exception ignored) {
                // 落到下面
            }
        }
        return JSON.createObjectNode().put("summary", s.trim()).put("confidence", "low");
    }

    // ==================== 查询与应用 ====================

    public ChannelResearchDO get(Long id) {
        ChannelResearchDO r = channelResearchMapper.selectById(id);
        if (r == null) {
            throw exception(RESEARCH_NOT_EXISTS);
        }
        return r;
    }

    public PageResult<ChannelResearchDO> page(PageParam req, Long customerId, Long clueId) {
        return channelResearchMapper.selectPage(req, customerId, clueId, null);
    }

    /** 每个客户最近一次背调（客户列表「背调结果」列） */
    public List<ChannelResearchDO> latestByCustomers(Collection<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        List<ChannelResearchDO> all = channelResearchMapper.selectList(new LambdaQueryWrapperX<ChannelResearchDO>()
                .in(ChannelResearchDO::getCustomerId, customerIds).orderByDesc(ChannelResearchDO::getId));
        Map<Long, ChannelResearchDO> latest = new LinkedHashMap<>();
        all.forEach(r -> latest.putIfAbsent(r.getCustomerId(), r));
        return new ArrayList<>(latest.values());
    }

    public void delete(Long id) {
        get(id);
        channelResearchMapper.deleteById(id);
    }

    /** 把报告里的公司名、官网、国家补到关联客户 / 线索的空字段上，并把概况追加到备注；返回更新了哪些字段 */
    public List<String> apply(Long id) {
        ChannelResearchDO r = get(id);
        if (!ChannelResearchDO.STATUS_DONE.equals(r.getStatus())) {
            throw exception(RESEARCH_INVALID, "背调还没有完成");
        }
        if (r.getCustomerId() == null && r.getClueId() == null) {
            throw exception(RESEARCH_INVALID, "这份背调没有关联客户或线索");
        }
        JsonNode rep = parseReport(r.getReport());
        String company = text(rep, "companyName");
        String country = text(rep, "countryCode");
        country = country != null && country.matches("(?i)[a-z]{2}") ? country.toUpperCase(Locale.ROOT) : null;
        String summary = text(rep, "summary");
        String note = summary == null ? null : "【AI 背调 " + java.time.LocalDate.now() + "】" + summary;
        List<String> changed = new ArrayList<>();
        if (r.getCustomerId() != null) {
            CrmCustomerDO c = crmCustomerMapper.selectById(r.getCustomerId());
            if (c != null) {
                CrmCustomerDO u = new CrmCustomerDO().setId(c.getId());
                if (StrUtil.isBlank(c.getCompanyName()) && company != null) { u.setCompanyName(company); changed.add("公司名称"); }
                if (StrUtil.isBlank(c.getWebsite()) && r.getWebsite() != null) { u.setWebsite(r.getWebsite()); changed.add("官网"); }
                if (StrUtil.isBlank(c.getCountryCode()) && country != null) { u.setCountryCode(country); changed.add("国家"); }
                if (note != null && !StrUtil.contains(c.getRemark(), note)) {
                    u.setRemark(StrUtil.maxLength(StrUtil.isBlank(c.getRemark()) ? note : c.getRemark() + "\n" + note, 1000));
                    changed.add("备注");
                }
                crmCustomerMapper.updateById(u);
            }
        } else {
            CrmClueDO c = crmClueMapper.selectById(r.getClueId());
            if (c != null) {
                CrmClueDO u = new CrmClueDO().setId(c.getId());
                if (StrUtil.isBlank(c.getCompanyName()) && company != null) { u.setCompanyName(company); changed.add("公司名称"); }
                if (StrUtil.isBlank(c.getWebsite()) && r.getWebsite() != null) { u.setWebsite(r.getWebsite()); changed.add("官网"); }
                if (StrUtil.isBlank(c.getCountryCode()) && country != null) { u.setCountryCode(country); changed.add("国家"); }
                if (note != null && !StrUtil.contains(c.getRemark(), note)) {
                    u.setRemark(StrUtil.maxLength(StrUtil.isBlank(c.getRemark()) ? note : c.getRemark() + "\n" + note, 1000));
                    changed.add("备注");
                }
                crmClueMapper.updateById(u);
            }
        }
        return changed;
    }

    private static String text(JsonNode n, String k) {
        JsonNode v = n.path(k);
        return v.isMissingNode() || v.isNull() || StrUtil.isBlank(v.asText()) ? null : v.asText().trim();
    }

}
