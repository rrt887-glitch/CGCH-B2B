package cn.iocoder.yudao.module.channel.service.dashboard;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Scope;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiDraftMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.dashboard.ChannelDashboardV3Mapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 看板「数据分析」：流量占比、AI 接待表现、商业转化、需要人工支持的事项。口径：
 * <ul>
 *     <li>一次咨询 = 时间范围内有客户来信的一个会话（邮件按会话线索归并）</li>
 *     <li>AI 1 小时回复率 = 客户首条来信后 1 小时内由 AI 回复的咨询 / 全部咨询</li>
 *     <li>AI 首响后 24 小时客户首回率 = AI 首次回复后 24 小时内客户再次来信的咨询 / AI 回复过的咨询</li>
 *     <li>AI 多轮沟通率 = AI 回复后客户又来信、AI 再回复（至少两轮）的咨询 / AI 回复过的咨询</li>
 * </ul>
 */
@Service
public class ChannelDashboardV3Service {

    public static final List<String> INTENTS = List.of("sales", "after_sales", "business", "invalid");
    /** 需要人工支持的分类：草稿原因 → 看板分类 */
    private static final Map<String, String> SUPPORT_TYPE = Map.of(
            ChannelAiDraftDO.REASON_HANDOFF, "handoff",
            ChannelAiDraftDO.REASON_FORBIDDEN, "compliance");

    @Resource
    private ChannelDashboardV3Mapper channelDashboardV3Mapper;
    @Resource
    private ChannelDashboardService channelDashboardService;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelAiDraftMapper channelAiDraftMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaConversationMapper channelWaConversationMapper;

    private record Conv(String intent, boolean aiReplied, boolean aiWithin1h, boolean customerBack24h, boolean multiRound) {
    }

    public Map<String, Object> overview(Long loginUserId, LocalDate startDate, LocalDate endDate) {
        Scope scope = channelDashboardService.resolveScope(loginUserId, null);
        Set<Long> users = scope.isAll() ? null : new HashSet<>(scope.getUserIds());
        List<Long> mailAccounts = users == null ? null : owned(channelMailAccountMapper.selectList().stream()
                .map(a -> Map.entry(a.getId(), Objects.requireNonNullElse(a.getUserId(), -1L))).toList(), users);
        List<Long> waAccounts = users == null ? null : owned(channelWaAccountMapper.selectList().stream()
                .map(a -> Map.entry(a.getId(), Objects.requireNonNullElse(a.getUserId(), -1L))).toList(), users);

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.plusDays(1).atStartOfDay();
        long days = Math.max(1, Duration.between(start, end).toDays());
        LocalDateTime prevStart = start.minusDays(days);

        List<Conv> convs = new ArrayList<>(conversations(channelDashboardV3Mapper.waMessages(start, end, waAccounts)));
        convs.addAll(conversations(channelDashboardV3Mapper.mailMessages(start, end, mailAccounts)));

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("startDate", startDate.toString());
        r.put("endDate", endDate.toString());
        r.put("updateTime", LocalDateTime.now().withNano(0).toString());
        r.put("traffic", traffic(convs));
        r.put("trend", trend(endDate, users, mailAccounts, waAccounts));

        long total = convs.size();
        long aiReplied = convs.stream().filter(Conv::aiReplied).count();
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("replies", aiReplies(start, end, mailAccounts, waAccounts));
        ai.put("tasks", n(channelDashboardV3Mapper.aiTasks(start, end)));
        ai.put("crmWrites", n(channelDashboardV3Mapper.aiCrmWrites(start, end, users)));
        ai.put("replyIn1hRate", rate(convs.stream().filter(Conv::aiWithin1h).count(), total));
        ai.put("customerBack24hRate", rate(convs.stream().filter(Conv::customerBack24h).count(), aiReplied));
        ai.put("multiRoundRate", rate(convs.stream().filter(Conv::multiRound).count(), aiReplied));
        r.put("ai", ai);

        long clues = n(channelDashboardV3Mapper.clueCount(start, end, users));
        long prevClues = n(channelDashboardV3Mapper.clueCount(prevStart, start, users));
        List<Map<String, Object>> biz = channelDashboardV3Mapper.businesses(start, end, users);
        long quoted = biz.stream().filter(b -> num(b.get("s")) >= 4 || num(b.get("e")) == 1).count();
        long won = biz.stream().filter(b -> num(b.get("e")) == 1 || num(b.get("s")) >= 8).count();
        Map<String, Object> biz2 = new LinkedHashMap<>();
        biz2.put("clues", clues);
        Double cluesChange = null; // 上一周期没有线索时无法计算环比
        if (prevClues > 0) {
            cluesChange = (clues - prevClues) / (double) prevClues;
        } else if (clues == 0) {
            cluesChange = 0.0;
        }
        biz2.put("cluesChange", cluesChange);
        biz2.put("inquiryToQuoteRate", rate(quoted, total));
        biz2.put("businessToWinRate", rate(won, biz.size()));
        biz2.put("businesses", biz.size());
        r.put("business", biz2);
        r.put("stages", channelDashboardV3Mapper.customerStages(users).stream()
                .map(m -> Map.<String, Object>of("stage", num(m.get("s")), "count", num(m.get("n"))))
                .sorted(java.util.Comparator.comparingLong(m -> (Long) m.get("stage"))).toList());
        r.put("support", support(mailAccounts, waAccounts));
        return r;
    }

    private static List<Long> owned(List<Map.Entry<Long, Long>> accounts, Set<Long> users) {
        List<Long> ids = new ArrayList<>(accounts.stream().filter(e -> users.contains(e.getValue())).map(Map.Entry::getKey).toList());
        if (ids.isEmpty()) {
            ids.add(-1L);
        }
        return ids;
    }

    /** 按会话归并消息，算每个咨询的意图与 AI 接待指标 */
    private static List<Conv> conversations(List<Map<String, Object>> rows) {
        List<Conv> out = new ArrayList<>();
        Object key = null;
        List<Map<String, Object>> group = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get("c");
            if (key != null && !key.equals(k)) {
                Conv c = toConv(group);
                if (c != null) {
                    out.add(c);
                }
                group = new ArrayList<>();
            }
            key = k;
            group.add(row);
        }
        Conv c = group.isEmpty() ? null : toConv(group);
        if (c != null) {
            out.add(c);
        }
        return out;
    }

    private static Conv toConv(List<Map<String, Object>> msgs) {
        LocalDateTime firstIn = null;
        LocalDateTime firstAi = null;
        String intent = null;
        int aiRounds = 0;
        boolean waitingCustomer = false;
        boolean customerBack24h = false;
        for (Map<String, Object> m : msgs) {
            int d = (int) num(m.get("d"));
            boolean ai = num(m.get("ai")) == 1;
            LocalDateTime t = time(m.get("t"));
            if (intent == null && m.get("intent") != null) {
                intent = String.valueOf(m.get("intent"));
            }
            if (d == 1) {
                if (firstIn == null) {
                    firstIn = t;
                }
                if (firstAi != null && t != null && !customerBack24h && !t.isAfter(firstAi.plusHours(24))) {
                    customerBack24h = true;
                }
                waitingCustomer = false;
            } else if (ai && firstIn != null) {
                if (firstAi == null) {
                    firstAi = t;
                }
                if (!waitingCustomer) {
                    aiRounds++;
                    waitingCustomer = true;
                }
            }
        }
        if (firstIn == null) {
            return null; // 只有发出、没有来信的会话不算咨询
        }
        boolean within1h = firstAi != null && firstIn != null && !firstAi.isAfter(firstIn.plusHours(1));
        return new Conv(intent, firstAi != null, within1h, customerBack24h, aiRounds >= 2);
    }

    private static Map<String, Object> traffic(List<Conv> convs) {
        Map<String, Object> t = new LinkedHashMap<>();
        long total = convs.size();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String i : INTENTS) {
            counts.put(i, convs.stream().filter(c -> i.equals(c.intent())).count());
        }
        long unclassified = convs.stream().filter(c -> c.intent() == null || !INTENTS.contains(c.intent())).count();
        t.put("total", total);
        t.put("counts", counts);
        t.put("unclassified", unclassified);
        long effective = counts.get("sales") + counts.get("after_sales") + counts.get("business");
        t.put("effective", effective);
        t.put("effectiveRate", rate(effective, total));
        return t;
    }

    /** 近 7 天有效流量占比 */
    private List<Map<String, Object>> trend(LocalDate endDate, Set<Long> users, List<Long> mailAccounts, List<Long> waAccounts) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = endDate.minusDays(i);
            List<Conv> convs = new ArrayList<>(conversations(channelDashboardV3Mapper.waMessages(d.atStartOfDay(), d.plusDays(1).atStartOfDay(), waAccounts)));
            convs.addAll(conversations(channelDashboardV3Mapper.mailMessages(d.atStartOfDay(), d.plusDays(1).atStartOfDay(), mailAccounts)));
            long eff = convs.stream().filter(c -> c.intent() != null && !"invalid".equals(c.intent()) && INTENTS.contains(c.intent())).count();
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("total", convs.size());
            day.put("effective", eff);
            day.put("rate", rate(eff, convs.size())); // 当天没有咨询时为 null
            out.add(day);
        }
        return out;
    }

    private long aiReplies(LocalDateTime start, LocalDateTime end, List<Long> mailAccounts, List<Long> waAccounts) {
        long n = 0;
        for (Map<String, Object> m : channelDashboardV3Mapper.waMessages(start, end, waAccounts)) {
            if (num(m.get("d")) == 2 && num(m.get("ai")) == 1) {
                n++;
            }
        }
        for (Map<String, Object> m : channelDashboardV3Mapper.mailMessages(start, end, mailAccounts)) {
            if (num(m.get("d")) == 2 && num(m.get("ai")) == 1) {
                n++;
            }
        }
        return n;
    }

    /** 待处理（未发出）的 AI 草稿中需要人工支持的事项，分会话与邮件 */
    private Map<String, Object> support(List<Long> mailAccounts, List<Long> waAccounts) {
        List<ChannelAiDraftDO> pending = channelAiDraftMapper.selectList(new LambdaQueryWrapperX<ChannelAiDraftDO>()
                .eq(ChannelAiDraftDO::getStatus, "pending")
                .in(ChannelAiDraftDO::getReason, List.of(ChannelAiDraftDO.REASON_HANDOFF, ChannelAiDraftDO.REASON_FORBIDDEN,
                        ChannelAiDraftDO.REASON_SEND_FAILED, ChannelAiDraftDO.REASON_NO_CREDIT, ChannelAiDraftDO.REASON_WINDOW_CLOSED))
                .orderByDesc(ChannelAiDraftDO::getId).last("LIMIT 200"));
        List<Map<String, Object>> im = new ArrayList<>();
        List<Map<String, Object>> mail = new ArrayList<>();
        for (ChannelAiDraftDO d : pending) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("draftId", d.getId());
            item.put("channel", d.getChannel());
            item.put("refId", d.getRefId());
            item.put("type", SUPPORT_TYPE.getOrDefault(d.getReason(), "other"));
            item.put("reason", d.getReason());
            item.put("detail", d.getReasonDetail());
            item.put("question", StrUtil.maxLength(d.getQuestion(), 200));
            item.put("time", d.getCreateTime());
            if ("mail".equals(d.getChannel())) {
                ChannelMailMessageDO m = channelMailMessageMapper.selectById(d.getRefId());
                if (m == null || (mailAccounts != null && !mailAccounts.contains(m.getAccountId()))) {
                    continue;
                }
                item.put("contact", StrUtil.blankToDefault(m.getFromName(), m.getFromAddress()));
                item.put("subject", m.getSubject());
                mail.add(item);
            } else {
                ChannelWaConversationDO c = channelWaConversationMapper.selectById(d.getRefId());
                if (c == null || (waAccounts != null && !waAccounts.contains(c.getAccountId()))) {
                    continue;
                }
                item.put("contact", c.contactLabel());
                item.put("platform", StrUtil.blankToDefault(c.getPlatform(), ChannelWaAccountDO.PLATFORM_WHATSAPP));
                im.add(item);
            }
        }
        return Map.of("im", im, "mail", mail);
    }

    private static Double rate(long a, long b) {
        return b == 0 ? null : Double.valueOf(a / (double) b); // 显式装箱：三元表达式里 null 与 double 混用会拆箱报错
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    private static long num(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (o instanceof Number x) {
            return x.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static LocalDateTime time(Object o) {
        if (o instanceof LocalDateTime t) {
            return t;
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        return null;
    }

}
