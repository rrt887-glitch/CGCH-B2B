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
 * 深度背调：读取客户官网的公开页面 + 我方与客户的往来记录 → AI 整理成背调报告（企业概况、主营产品、
 * 采购信号、沟通切入点、风险提示），可一键补充到 CRM 客户 / 线索。只收集企业层面的公开商业信息。
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
        if (site == null && customer == null && clue == null) {
            throw exception(RESEARCH_INVALID, "请填写官网，或选择一个客户 / 线索");
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
                pages = fetcher.fetchSite(URI.create(r.getWebsite()));
            } catch (WebsiteFetcher.FetchException e) {
                siteNote = e.getMessage();
            }
        }
        // 2. 我方往来记录
        List<String> history = history(r);
        if (pages.isEmpty() && history.isEmpty()) {
            fail(id, StrUtil.blankToDefault(siteNote, "没有可用的资料：官网无法访问，也没有往来记录"));
            return;
        }
        // 3. AI 整理
        try {
            LlmClient.Result res = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(), List.of(
                    LlmClient.Message.system(systemPrompt()), LlmClient.Message.user(userPrompt(r, pages, history, siteNote))), 0.2);
            JsonNode report = parseReport(res.content());
            long credits = channelCreditService.consume("research", id, r.getUserId(), res.promptTokens() + res.completionTokens(), "深度背调");
            List<Map<String, String>> sources = pages.stream().map(p -> Map.of("url", p.url(), "title", StrUtil.nullToEmpty(p.title()),
                    "snippet", StrUtil.maxLength(p.text(), SNIPPET))).collect(Collectors.toList());
            String name = StrUtil.blankToDefault(report.path("companyName").asText(null), r.getCompanyName());
            channelResearchMapper.updateById(new ChannelResearchDO().setId(id).setStatus(ChannelResearchDO.STATUS_DONE)
                    .setReport(report.toString()).setSources(JsonUtils.toJsonString(sources)).setCredits(credits)
                    .setCompanyName(StrUtil.maxLength(name, 200)).setReasonDetail(StrUtil.nullToEmpty(siteNote)));
        } catch (Exception e) {
            fail(id, "AI 整理失败：" + e.getMessage());
        }
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
        return "你是外贸企业的客户背调分析师。根据提供的「客户官网公开页面」和「我方与客户的往来记录」，"
                + "为业务员整理一份客户背调报告，帮助判断客户类型和跟进方向。\n"
                + "规则：\n1. 只使用提供的资料，资料里没有的写 null 或空数组，不要猜测或编造（尤其是规模、营收、成立时间）。\n"
                + "2. 只整理企业层面的商业信息；联系方式只记录官网公开的公司邮箱、电话、地址，不记录员工个人信息。\n"
                + "3. 用简体中文撰写，公司名、产品名保留原文。\n"
                + "4. 只输出 JSON，字段：companyName, countryCode（ISO 二字码）, summary（2~4 句概况）, "
                + "businessType（manufacturer/brand/distributor/wholesaler/retailer/e-commerce/trading/other 之一）, "
                + "mainProducts（数组）, markets（数组）, scale（规模线索，原文依据）, certifications（数组）, "
                + "companyContacts（数组，官网公开的公司联系方式）, purchasingSignals（数组：他们可能向我们采购什么、依据是什么）, "
                + "talkingPoints（数组：业务员跟进时的切入点）, risks（数组：需要注意的风险或疑点）, confidence（high/medium/low）。";
    }

    private String userPrompt(ChannelResearchDO r, List<WebsiteFetcher.Page> pages, List<String> history, String siteNote) {
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
            sb.append("\n## 我方企业与产品（用于判断采购信号）\n").append(StrUtil.maxLength(ours, 2000)).append("\n");
        }
        if (!pages.isEmpty()) {
            sb.append("\n## 客户官网公开页面\n");
            for (WebsiteFetcher.Page p : pages) {
                sb.append("### ").append(p.title()).append("（").append(p.url()).append("）\n").append(p.text()).append("\n\n");
            }
        }
        if (!history.isEmpty()) {
            sb.append("\n## 我方与客户的往来记录（最近）\n").append(String.join("\n", history)).append("\n");
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
