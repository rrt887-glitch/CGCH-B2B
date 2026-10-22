package cn.iocoder.yudao.module.channel.service.assistant;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Scope;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiDraftMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardService;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 对话助手可调用的工具。全部只读，并按当前用户的数据权限过滤（只能查到自己有权看到的客户和消息）。
 */
@Component
public class ChannelAssistantTools {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_RESULT = 6000;

    @Resource
    private CrmCustomerMapper crmCustomerMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelAiDraftMapper channelAiDraftMapper;
    @Resource
    private ChannelDashboardService channelDashboardService;

    /** 本次调用的上下文：当前用户与其可见范围 */
    public record Ctx(Long userId, Set<Long> visibleUsers, List<Long> mailAccounts, List<Long> waAccounts) {
        boolean all() {
            return visibleUsers == null;
        }
    }

    public Ctx context(Long userId) {
        Scope s = channelDashboardService.resolveScope(userId, null);
        Set<Long> users = s.isAll() ? null : new HashSet<>(s.getUserIds());
        List<Long> mail = new ArrayList<>(), wa = new ArrayList<>();
        for (ChannelMailAccountDO a : channelMailAccountMapper.selectList()) {
            if (users == null || users.contains(a.getUserId())) {
                mail.add(a.getId());
            }
        }
        for (ChannelWaAccountDO a : channelWaAccountMapper.selectList()) {
            if (users == null || users.contains(a.getUserId())) {
                wa.add(a.getId());
            }
        }
        return new Ctx(userId, users, mail, wa);
    }

    // ==================== 工具定义 ====================

    public ArrayNode definitions() {
        ArrayNode tools = JSON.createArrayNode();
        tool(tools, "search_customers", "按名称、公司或邮箱搜索 CRM 客户，返回基本信息和最近联系时间",
                prop("keyword", "string", "客户名、公司名或邮箱的一部分；不填则返回最近更新的客户"),
                prop("limit", "integer", "最多返回条数，默认 10"));
        tool(tools, "get_customer", "查看某位客户的详细资料、最近的邮件和 WhatsApp 往来",
                prop("id", "integer", "客户编号（知道时优先使用）"), prop("name", "string", "客户名或公司名（不知道编号时使用）"));
        tool(tools, "followups_due", "列出需要跟进的客户：下次联系时间已到期或即将到期的，以及很久没联系的",
                prop("days", "integer", "下次联系时间在今天起多少天内，默认 7"),
                prop("inactive_days", "integer", "超过多少天未联系算久未联系，默认 30"));
        tool(tools, "search_messages", "在邮件和 WhatsApp 消息中按关键词搜索",
                prop("keyword", "string", "关键词，必填"), prop("days", "integer", "只看最近多少天，默认 30"));
        tool(tools, "get_stats", "查看询盘量、回复率、AI 处理量、线索、智点消耗等统计",
                prop("days", "integer", "最近多少天，默认 30"));
        tool(tools, "pending_drafts", "列出 AI 已起草但还等业务员确认或处理的回复，以及原因");
        return tools;
    }

    private static ObjectNode prop(String name, String type, String desc) {
        ObjectNode p = JSON.createObjectNode();
        p.put("type", type).put("description", desc);
        return p.put("x-name", name);
    }

    private static void tool(ArrayNode tools, String name, String desc, ObjectNode... props) {
        ObjectNode fn = tools.addObject().put("type", "function").putObject("function");
        fn.put("name", name).put("description", desc);
        ObjectNode params = fn.putObject("parameters").put("type", "object");
        ObjectNode ps = params.putObject("properties");
        for (ObjectNode p : props) {
            String n = p.remove("x-name").asText();
            ps.set(n, p);
        }
        if ("search_messages".equals(name)) {
            params.putArray("required").add("keyword");
        }
    }

    // ==================== 执行 ====================

    /** 执行一个工具，返回给模型的 JSON 文本 */
    public String call(Ctx ctx, String name, String argsJson) {
        JsonNode args;
        try {
            args = StrUtil.isBlank(argsJson) ? JSON.createObjectNode() : JSON.readTree(argsJson);
        } catch (Exception e) {
            return "{\"error\":\"参数不是合法的 JSON\"}";
        }
        Object out = switch (name) {
            case "search_customers" -> searchCustomers(ctx, text(args, "keyword"), num(args, "limit", 10, 1, 30));
            case "get_customer" -> getCustomer(ctx, args.hasNonNull("id") ? args.get("id").asLong() : null, text(args, "name"));
            case "followups_due" -> followups(ctx, num(args, "days", 7, 0, 90), num(args, "inactive_days", 30, 1, 3650));
            case "search_messages" -> searchMessages(ctx, text(args, "keyword"), num(args, "days", 30, 1, 365));
            case "get_stats" -> stats(ctx, num(args, "days", 30, 1, 366));
            case "pending_drafts" -> pendingDrafts(ctx);
            default -> Map.of("error", "未知工具：" + name);
        };
        return StrUtil.maxLength(JsonUtils.toJsonString(out), MAX_RESULT);
    }

    private static String text(JsonNode a, String k) {
        return a.hasNonNull(k) && StrUtil.isNotBlank(a.get(k).asText()) ? a.get(k).asText().trim() : null;
    }

    private static int num(JsonNode a, String k, int def, int min, int max) {
        int v = a.hasNonNull(k) ? a.get(k).asInt(def) : def;
        return Math.max(min, Math.min(max, v));
    }

    private LambdaQueryWrapperX<CrmCustomerDO> customerQuery(Ctx ctx) {
        LambdaQueryWrapperX<CrmCustomerDO> q = new LambdaQueryWrapperX<>();
        if (!ctx.all()) {
            q.in(CrmCustomerDO::getOwnerUserId, ctx.visibleUsers().isEmpty() ? List.of(-1L) : ctx.visibleUsers());
        }
        return q;
    }

    private static Map<String, Object> brief(CrmCustomerDO c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("company", c.getCompanyName());
        m.put("country", c.getCountryCode());
        m.put("email", c.getEmail());
        m.put("whatsapp", c.getWhatsapp());
        m.put("ownerUserId", c.getOwnerUserId());
        m.put("dealStatus", Boolean.TRUE.equals(c.getDealStatus()) ? "已成交" : "未成交");
        m.put("lastContact", c.getContactLastTime() == null ? null : c.getContactLastTime().format(DAY));
        m.put("nextContact", c.getContactNextTime() == null ? null : c.getContactNextTime().format(DAY));
        return m;
    }

    private Object searchCustomers(Ctx ctx, String keyword, int limit) {
        LambdaQueryWrapperX<CrmCustomerDO> q = customerQuery(ctx);
        if (keyword != null) {
            q.and(w -> w.like(CrmCustomerDO::getName, keyword).or().like(CrmCustomerDO::getCompanyName, keyword)
                    .or().like(CrmCustomerDO::getEmail, keyword));
        }
        q.orderByDesc(CrmCustomerDO::getUpdateTime).last("LIMIT " + limit);
        List<Map<String, Object>> list = crmCustomerMapper.selectList(q).stream().map(ChannelAssistantTools::brief).toList();
        return Map.of("count", list.size(), "customers", list);
    }

    private Object getCustomer(Ctx ctx, Long id, String name) {
        LambdaQueryWrapperX<CrmCustomerDO> q = customerQuery(ctx);
        if (id != null) {
            q.eq(CrmCustomerDO::getId, id);
        } else if (name != null) {
            q.and(w -> w.like(CrmCustomerDO::getName, name).or().like(CrmCustomerDO::getCompanyName, name));
        } else {
            return Map.of("error", "请提供客户编号或名称");
        }
        List<CrmCustomerDO> found = crmCustomerMapper.selectList(q.last("LIMIT 5"));
        if (found.isEmpty()) {
            return Map.of("found", false, "message", "没有找到该客户，或你没有权限查看");
        }
        if (found.size() > 1 && id == null) {
            boolean exact = found.stream().anyMatch(c -> c.getName().equalsIgnoreCase(name));
            if (!exact) {
                return Map.of("found", false, "message", "找到多位客户，请确认是哪一位",
                        "candidates", found.stream().map(ChannelAssistantTools::brief).toList());
            }
            found = found.stream().filter(c -> c.getName().equalsIgnoreCase(name)).toList();
        }
        CrmCustomerDO c = found.get(0);
        Map<String, Object> m = brief(c);
        m.put("website", c.getWebsite());
        m.put("remark", StrUtil.maxLength(c.getRemark(), 500));
        m.put("lastContactContent", StrUtil.maxLength(c.getContactLastContent(), 300));
        List<Map<String, Object>> mails = new ArrayList<>();
        for (ChannelMailMessageDO x : channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .eq(ChannelMailMessageDO::getCustomerId, c.getId()).ne(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_DRAFT)
                .orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 6"))) {
            mails.add(Map.of("direction", x.getDirection() == ChannelMailMessageDO.DIRECTION_IN ? "客户来信" : "我方发出",
                    "time", x.getSentTime() == null ? "" : x.getSentTime().format(TIME), "subject", StrUtil.nullToEmpty(x.getSubject()),
                    "summary", HtmlText.snippet(x.getTextBody(), x.getHtmlBody(), 200)));
        }
        m.put("recentMails", mails);
        List<Map<String, Object>> wa = new ArrayList<>();
        for (ChannelWaConversationDO conv : channelWaConversationMapper.selectList(new LambdaQueryWrapperX<ChannelWaConversationDO>()
                .eq(ChannelWaConversationDO::getCustomerId, c.getId()).last("LIMIT 2"))) {
            List<ChannelWaMessageDO> msgs = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .eq(ChannelWaMessageDO::getConversationId, conv.getId()).orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 6"));
            Collections.reverse(msgs);
            msgs.forEach(x -> wa.add(Map.of("direction", x.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "客户" : "我方",
                    "time", x.getMessageTime() == null ? "" : x.getMessageTime().format(TIME),
                    "text", StrUtil.maxLength(StrUtil.blankToDefault(x.getTextBody(), "[" + x.getType() + "]"), 200))));
        }
        m.put("recentWhatsApp", wa);
        return m;
    }

    private Object followups(Ctx ctx, int days, int inactiveDays) {
        LocalDateTime now = LocalDateTime.now();
        List<Map<String, Object>> due = crmCustomerMapper.selectList(customerQuery(ctx)
                        .isNotNull(CrmCustomerDO::getContactNextTime).le(CrmCustomerDO::getContactNextTime, now.plusDays(days))
                        .orderByAsc(CrmCustomerDO::getContactNextTime).last("LIMIT 15"))
                .stream().map(c -> {
                    Map<String, Object> m = brief(c);
                    m.put("overdue", c.getContactNextTime().isBefore(now));
                    return m;
                }).toList();
        LocalDateTime cutoff = now.minusDays(inactiveDays);
        LambdaQueryWrapperX<CrmCustomerDO> q = customerQuery(ctx);
        q.and(w -> w.lt(CrmCustomerDO::getContactLastTime, cutoff)
                .or(x -> x.isNull(CrmCustomerDO::getContactLastTime).lt(CrmCustomerDO::getCreateTime, cutoff)));
        List<Map<String, Object>> inactive = crmCustomerMapper.selectList(q.orderByAsc(CrmCustomerDO::getContactLastTime).last("LIMIT 15"))
                .stream().map(c -> {
                    Map<String, Object> m = brief(c);
                    LocalDateTime last = c.getContactLastTime() != null ? c.getContactLastTime() : c.getCreateTime();
                    m.put("daysSinceContact", last == null ? null : Duration.between(last, now).toDays());
                    return m;
                }).toList();
        // 客户来信还没回复的邮件
        List<Map<String, Object>> unreplied = new ArrayList<>();
        if (!ctx.mailAccounts().isEmpty()) {
            for (ChannelMailMessageDO x : channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                    .in(ChannelMailMessageDO::getAccountId, ctx.mailAccounts())
                    .eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_IN)
                    .eq(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_INBOX)
                    .eq(ChannelMailMessageDO::getReplied, false).orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 10"))) {
                unreplied.add(Map.of("from", StrUtil.blankToDefault(x.getFromName(), StrUtil.nullToEmpty(x.getFromAddress())),
                        "subject", StrUtil.nullToEmpty(x.getSubject()), "time", x.getSentTime() == null ? "" : x.getSentTime().format(TIME)));
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("today", now.format(DAY));
        m.put("nextContactDue", due);
        m.put("inactiveCustomers", inactive);
        m.put("unrepliedMails", unreplied);
        return m;
    }

    private Object searchMessages(Ctx ctx, String keyword, int days) {
        if (keyword == null) {
            return Map.of("error", "请提供关键词");
        }
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<Map<String, Object>> out = new ArrayList<>();
        if (!ctx.mailAccounts().isEmpty()) {
            for (ChannelMailMessageDO x : channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                    .in(ChannelMailMessageDO::getAccountId, ctx.mailAccounts()).ge(ChannelMailMessageDO::getSentTime, since)
                    .ne(ChannelMailMessageDO::getFolder, ChannelMailMessageDO.FOLDER_DRAFT)
                    .and(w -> w.like(ChannelMailMessageDO::getSubject, keyword).or().like(ChannelMailMessageDO::getSnippet, keyword)
                            .or().like(ChannelMailMessageDO::getFromAddress, keyword))
                    .orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 10"))) {
                out.add(Map.of("channel", "邮件", "direction", x.getDirection() == ChannelMailMessageDO.DIRECTION_IN ? "收到" : "发出",
                        "from", StrUtil.blankToDefault(x.getFromName(), StrUtil.nullToEmpty(x.getFromAddress())),
                        "subject", StrUtil.nullToEmpty(x.getSubject()), "summary", StrUtil.maxLength(x.getSnippet(), 200),
                        "time", x.getSentTime() == null ? "" : x.getSentTime().format(TIME)));
            }
        }
        if (!ctx.waAccounts().isEmpty()) {
            for (ChannelWaMessageDO x : channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .in(ChannelWaMessageDO::getAccountId, ctx.waAccounts()).ge(ChannelWaMessageDO::getMessageTime, since)
                    .like(ChannelWaMessageDO::getTextBody, keyword).orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 10"))) {
                ChannelWaConversationDO conv = channelWaConversationMapper.selectById(x.getConversationId());
                out.add(Map.of("channel", "WhatsApp", "direction", x.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "收到" : "发出",
                        "contact", conv == null ? "" : conv.contactLabel(),
                        "text", StrUtil.maxLength(x.getTextBody(), 200), "time", x.getMessageTime() == null ? "" : x.getMessageTime().format(TIME)));
            }
        }
        return Map.of("count", out.size(), "messages", out);
    }

    private Object stats(Ctx ctx, int days) {
        ChannelDashboardRespVO vo = channelDashboardService.overview(ctx.userId(), days, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("days", days);
        m.put("scope", ctx.all() ? "全部业务员" : "本人可见范围");
        m.put("current", vo.getCurrent());
        m.put("previous", vo.getPrevious());
        m.put("fieldNotes", "inquiries 新询盘，replyRate 回复率(0~1)，aiShare AI 发出占比，aiHandoff 转人工，aiPending 待确认草稿，"
                + "cluesCreated 新线索，cluesConverted 转客户，creditsConsumed 智点消耗");
        return m;
    }

    private Object pendingDrafts(Ctx ctx) {
        List<Map<String, Object>> out = new ArrayList<>();
        Set<Long> mail = new HashSet<>(ctx.mailAccounts()), wa = new HashSet<>(ctx.waAccounts());
        for (ChannelAiDraftDO d : channelAiDraftMapper.selectList(new LambdaQueryWrapperX<ChannelAiDraftDO>()
                .eq(ChannelAiDraftDO::getStatus, ChannelAiDraftDO.STATUS_PENDING).orderByDesc(ChannelAiDraftDO::getId).last("LIMIT 60"))) {
            String who;
            if (ChannelAiDraftDO.CHANNEL_MAIL.equals(d.getChannel())) {
                ChannelMailMessageDO m = channelMailMessageMapper.selectById(d.getRefId());
                if (m == null || !mail.contains(m.getAccountId())) {
                    continue;
                }
                who = StrUtil.blankToDefault(m.getFromName(), m.getFromAddress()) + " · " + StrUtil.nullToEmpty(m.getSubject());
            } else {
                ChannelWaConversationDO c = channelWaConversationMapper.selectById(d.getRefId());
                if (c == null || !wa.contains(c.getAccountId())) {
                    continue;
                }
                who = c.contactLabel();
            }
            out.add(Map.of("channel", d.getChannel(), "customer", who, "reason", StrUtil.nullToEmpty(d.getReason()),
                    "time", d.getCreateTime() == null ? "" : d.getCreateTime().format(TIME)));
            if (out.size() >= 15) {
                break;
            }
        }
        return Map.of("count", out.size(), "drafts", out,
                "reasonNotes", "handoff 需人工处理，forbidden 含违禁词，human_active 业务员在跟进，not_hosted 未开启 AI 托管，"
                        + "draft_mode 只起草模式，window_closed WhatsApp 窗口关闭，manual 手动起草，no_credit 智点不足");
    }

}
