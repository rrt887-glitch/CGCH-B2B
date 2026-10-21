package cn.iocoder.yudao.module.channel.service.report;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Totals;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiReportDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiDraftMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiReportMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_REPORT_NOT_EXISTS;

/**
 * AI 工作报告：汇总一天或一周里 AI 接待的情况，列出转人工的对话和知识库缺口，并由大模型写一段总结。
 */
@Service
@Slf4j
public class ChannelAiReportService {

    public static final String MODE_AUTO = "auto";
    public static final String MODE_MANUAL = "manual";
    private static final int LIST_LIMIT = 20;
    private static final Map<String, String> REASON = Map.of(
            "draft_mode", "只起草模式", "handoff", "需人工处理", "forbidden", "含违禁词", "human_active", "业务员在跟进",
            "not_hosted", "未开启 AI 托管", "window_closed", "WhatsApp 窗口已关闭", "send_failed", "发送失败",
            "manual", "手动起草", "no_credit", "智点不足");

    @Resource
    private ChannelAiReportMapper channelAiReportMapper;
    @Resource
    private ChannelAiDraftMapper channelAiDraftMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelDashboardService channelDashboardService;
    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private ChannelCreditService channelCreditService;

    /** 报告覆盖的日期区间：日报为当天；周报为该日期所在的周一到周日 */
    public static LocalDate[] period(String type, LocalDate date) {
        if (ChannelAiReportDO.TYPE_WEEKLY.equals(type)) {
            LocalDate monday = date.with(DayOfWeek.MONDAY);
            return new LocalDate[]{monday, monday.plusDays(6)};
        }
        return new LocalDate[]{date, date};
    }

    public boolean exists(String type, LocalDate date) {
        return channelAiReportMapper.selectByPeriod(type, period(type, date)[0]) != null;
    }

    /** 生成（或重新生成）一份报告，返回编号 */
    public Long generate(String type, LocalDate date, String mode, Long userId) {
        type = ChannelAiReportDO.TYPE_WEEKLY.equals(type) ? ChannelAiReportDO.TYPE_WEEKLY : ChannelAiReportDO.TYPE_DAILY;
        LocalDate[] p = period(type, date);
        LocalDateTime start = p[0].atStartOfDay();
        LocalDateTime end = p[1].plusDays(1).atStartOfDay();
        long days = p[1].toEpochDay() - p[0].toEpochDay() + 1;

        Map<String, Long> pending = new LinkedHashMap<>();
        Totals cur = channelDashboardService.totalsAll(start, end, pending);
        Totals prev = channelDashboardService.totalsAll(start.minusDays(days), start, null);
        List<Map<String, Object>> handoffs = channelAiDraftMapper.selectHandoffs(start, end, LIST_LIMIT).stream().map(this::item).toList();
        List<Map<String, Object>> gaps = gaps(channelAiDraftMapper.selectKnowledgeGaps(start, end, LIST_LIMIT * 3));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("current", cur);
        stats.put("previous", prev);
        stats.put("pendingReasons", pending);
        stats.put("handoffs", handoffs);
        stats.put("gaps", gaps);

        ChannelAiReportDO old = channelAiReportMapper.selectByPeriod(type, p[0]);
        if (old != null) {
            channelAiReportMapper.deleteById(old.getId());
        }
        ChannelAiReportDO r = ChannelAiReportDO.builder().periodType(type).periodStart(p[0]).periodEnd(p[1])
                .stats(JsonUtils.toJsonString(stats)).summary(templateSummary(type, p, cur, prev, pending, handoffs, gaps))
                .aiGenerated(false).credits(0L).generateMode(mode).userId(userId).build();
        channelAiReportMapper.insert(r);

        ChannelAiConfigDO cfg = channelAiService.getConfig();
        if (StrUtil.isAllNotBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel()) && channelCreditService.hasBalance()) {
            try {
                LlmClient.Result res = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(), List.of(
                        LlmClient.Message.system(llmSystem(cfg)),
                        LlmClient.Message.user(llmUser(type, p, cur, prev, pending, handoffs, gaps))), 0.3);
                long credits = channelCreditService.consume("report", r.getId(), userId,
                        res.promptTokens() + res.completionTokens(), "AI 工作报告");
                channelAiReportMapper.updateById(new ChannelAiReportDO().setId(r.getId())
                        .setSummary(res.content()).setAiGenerated(true).setCredits(credits));
            } catch (Exception e) {
                log.warn("[generate][报告({}) 大模型总结失败，保留模板总结] {}", r.getId(), e.getMessage());
            }
        }
        return r.getId();
    }

    public ChannelAiReportDO get(Long id) {
        ChannelAiReportDO r = channelAiReportMapper.selectById(id);
        if (r == null) {
            throw exception(AI_REPORT_NOT_EXISTS);
        }
        return r;
    }

    public PageResult<ChannelAiReportDO> page(PageParam req, String type) {
        return channelAiReportMapper.selectPage(req, StrUtil.emptyToNull(type));
    }

    public void delete(Long id) {
        get(id);
        channelAiReportMapper.deleteById(id);
    }

    // ==================== 明细 ====================

    /** 一条 AI 记录 → 报告里展示的条目：渠道、对象、客户、问题、原因 */
    private Map<String, Object> item(ChannelAiDraftDO d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("draftId", d.getId());
        m.put("channel", d.getChannel());
        m.put("refId", d.getRefId());
        m.put("customer", customerLabel(d));
        m.put("question", StrUtil.maxLength(StrUtil.nullToEmpty(d.getQuestion()), 200));
        m.put("detail", d.getReasonDetail());
        m.put("time", d.getCreateTime() == null ? null : d.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        return m;
    }

    private String customerLabel(ChannelAiDraftDO d) {
        if (ChannelAiDraftDO.CHANNEL_MAIL.equals(d.getChannel())) {
            ChannelMailMessageDO m = channelMailMessageMapper.selectById(d.getRefId());
            return m == null ? "邮件 #" + d.getRefId()
                    : StrUtil.blankToDefault(m.getFromName(), m.getFromAddress()) + " · " + StrUtil.nullToEmpty(m.getSubject());
        }
        ChannelWaConversationDO c = channelWaConversationMapper.selectById(d.getRefId());
        return c == null ? "WhatsApp #" + d.getRefId() : StrUtil.blankToDefault(c.getProfileName(), "+" + c.getWaId());
    }

    /** 同一对象只保留最近一次没命中的问题；之后又命中了资料的（已补上）不算缺口。最多 LIST_LIMIT 条 */
    private List<Map<String, Object>> gaps(List<ChannelAiDraftDO> drafts) {
        Set<String> seen = new HashSet<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ChannelAiDraftDO d : drafts) {
            if (!seen.add(d.getChannel() + ":" + d.getRefId())
                    || channelAiDraftMapper.existsHitAfter(d.getChannel(), d.getRefId(), d.getId())) {
                continue;
            }
            out.add(item(d));
            if (out.size() >= LIST_LIMIT) {
                break;
            }
        }
        return out;
    }

    // ==================== 总结 ====================

    private static String title(String type, LocalDate[] p) {
        return ChannelAiReportDO.TYPE_WEEKLY.equals(type) ? "周报（" + p[0] + " 至 " + p[1] + "）" : "日报（" + p[0] + "）";
    }

    private static String change(long a, long b) {
        if (b == 0) {
            return a == 0 ? "持平" : "上期为 0";
        }
        long pct = Math.round((a - b) * 100.0 / b);
        return pct == 0 ? "持平" : (pct > 0 ? "增加 " : "减少 ") + Math.abs(pct) + "%";
    }

    private static String pct(double v) {
        return Math.round(v * 1000) / 10.0 + "%";
    }

    /** 没有配置大模型或智点不足时使用的模板总结 */
    static String templateSummary(String type, LocalDate[] p, Totals c, Totals prev, Map<String, Long> pending,
                                  List<Map<String, Object>> handoffs, List<Map<String, Object>> gaps) {
        StringBuilder sb = new StringBuilder();
        sb.append("【概况】").append(title(type, p)).append("共收到新询盘 ").append(c.getInquiries())
                .append(" 个（邮件 ").append(c.getMailIn()).append("，WhatsApp 会话 ").append(c.getWaConversations())
                .append("），较上期").append(change(c.getInquiries(), prev.getInquiries())).append("；回复率 ")
                .append(pct(c.getReplyRate())).append("，AI 发出消息占 ").append(pct(c.getAiShare())).append("。\n");
        sb.append("【AI 处理】生成回复 ").append(c.getAiDrafts()).append(" 份，已发出 ").append(c.getAiSent())
                .append("，待人工确认 ").append(c.getAiPending()).append("，转人工 ").append(c.getAiHandoff())
                .append("；消耗智点 ").append(c.getCreditsConsumed()).append("。\n");
        if (!pending.isEmpty()) {
            sb.append("【待确认原因】");
            pending.forEach((k, v) -> sb.append(REASON.getOrDefault(k, k)).append(" ").append(v).append("；"));
            sb.append("\n");
        }
        sb.append("【线索】新增线索 ").append(c.getCluesCreated()).append(" 条（询盘自动建档 ")
                .append(c.getCluesFromMail() + c.getCluesFromWhatsApp()).append("），转为客户 ").append(c.getCluesConverted()).append("。\n");
        if (!handoffs.isEmpty()) {
            sb.append("【需要关注】有 ").append(handoffs.size()).append(" 个对话被 AI 转交人工，请尽快跟进。\n");
        }
        if (!gaps.isEmpty()) {
            sb.append("【建议补充知识库】有 ").append(gaps.size()).append(" 个客户问题在知识库里找不到资料，补充后 AI 可以直接回答。\n");
        }
        return sb.toString().trim();
    }

    private static String llmSystem(ChannelAiConfigDO cfg) {
        return "你是外贸企业的销售运营助理，负责撰写数字员工「" + StrUtil.blankToDefault(cfg.getAgentName(), "AI") + "」的工作报告。\n"
                + "根据用户提供的统计数据，用简体中文写一份给销售主管看的工作报告，分为【概况】【需要关注】【建议补充知识库】【下一步建议】四段，"
                + "每段 1~4 句或简短条目。只依据给出的数据，不要编造数字；没有内容的段落写「无」。不要使用 Markdown 标题符号。";
    }

    private static String llmUser(String type, LocalDate[] p, Totals c, Totals prev, Map<String, Long> pending,
                                  List<Map<String, Object>> handoffs, List<Map<String, Object>> gaps) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("报告", title(type, p));
        m.put("本期", c);
        m.put("上期", prev);
        Map<String, Long> reasons = new LinkedHashMap<>();
        pending.forEach((k, v) -> reasons.put(REASON.getOrDefault(k, k), v));
        m.put("待人工确认原因", reasons);
        m.put("转人工对话", handoffs.stream().map(h -> h.get("customer") + "：" + h.get("question")).toList());
        m.put("知识库没有资料的问题", gaps.stream().map(g -> String.valueOf(g.get("question"))).toList());
        return "字段说明：inquiries 新询盘，replyRate 回复率，aiShare AI 发出占比，aiDrafts AI 回复数，aiSent 已发出，"
                + "aiPending 待确认，aiHandoff 转人工，creditsConsumed 消耗智点，cluesCreated 新线索，cluesConverted 转客户。\n"
                + JsonUtils.toJsonString(m);
    }

}
