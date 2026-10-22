package cn.iocoder.yudao.module.channel.service.overview;

import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Scope;
import cn.iocoder.yudao.module.channel.dal.mysql.dashboard.ChannelCustomerOverviewMapper;
import cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 客户概览：客户总量、成交率、活跃度、国家 / 来源 / 等级分布、商机阶段、最活跃客户。按数据权限过滤。
 */
@Service
public class ChannelCustomerOverviewService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Resource
    private ChannelCustomerOverviewMapper channelCustomerOverviewMapper;
    @Resource
    private ChannelDashboardService channelDashboardService;

    public Map<String, Object> overview(Long loginUserId, int days, Long userId) {
        days = Math.max(1, Math.min(days, 366));
        Scope scope = channelDashboardService.resolveScope(loginUserId, userId);
        Set<Long> users = scope.getUserId() != null ? Set.of(scope.getUserId()) : scope.isAll() ? null : new HashSet<>(scope.getUserIds());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = LocalDate.now().minusDays(days - 1L).atStartOfDay();

        Map<String, Object> t = channelCustomerOverviewMapper.totals(now, since, users);
        Map<String, Object> totals = new LinkedHashMap<>();
        long total = num(t, "total"), dealt = num(t, "dealt");
        totals.put("total", total);
        totals.put("dealt", dealt);
        totals.put("dealRate", total == 0 ? 0 : Math.round(dealt * 1000.0 / total) / 1000.0);
        totals.put("created", num(t, "created"));
        totals.put("pool", users == null ? num(t, "pool") : 0);
        totals.put("aiHosted", num(t, "hosted"));
        totals.put("overdueFollowUp", num(t, "overdue"));
        Map<String, Long> recency = new LinkedHashMap<>();
        recency.put("d7", num(t, "d7"));
        recency.put("d30", num(t, "d30"));
        recency.put("d90", num(t, "d90"));
        recency.put("older", num(t, "older"));
        recency.put("never", num(t, "never"));
        totals.put("active30", recency.get("d7") + recency.get("d30"));
        totals.put("dormant", recency.get("older") + recency.get("never"));

        // 最近 12 个月新增，补齐没有数据的月份
        LocalDate firstMonth = LocalDate.now().withDayOfMonth(1).minusMonths(11);
        Map<String, Long> monthly = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            monthly.put(firstMonth.plusMonths(i).format(MONTH), 0L);
        }
        for (Map<String, Object> r : channelCustomerOverviewMapper.monthly(firstMonth.atStartOfDay(), users)) {
            monthly.computeIfPresent(String.valueOf(r.get("m")), (k, v) -> v + num(r, "n"));
        }

        Long typeId = channelCustomerOverviewMapper.defaultStatusType();
        List<Map<String, Object>> funnel = new ArrayList<>();
        Map<String, Object> ended = new LinkedHashMap<>();
        if (typeId != null) {
            for (Map<String, Object> r : channelCustomerOverviewMapper.funnel(typeId, users)) {
                funnel.add(Map.of("id", num(r, "id"), "name", String.valueOf(r.get("name")), "percent", num(r, "percent"),
                        "count", num(r, "n"), "amount", dec(r, "amount")));
            }
            Map<Long, String> label = Map.of(1L, "won", 2L, "lost", 3L, "invalid");
            for (Map<String, Object> r : channelCustomerOverviewMapper.ended(typeId, users)) {
                ended.put(label.getOrDefault(num(r, "k"), "other"), Map.of("count", num(r, "n"), "amount", dec(r, "amount")));
            }
        }

        List<Map<String, Object>> top = new ArrayList<>();
        for (Map<String, Object> r : channelCustomerOverviewMapper.topActive(since, users)) {
            long msgs = num(r, "mails") + num(r, "was");
            if (msgs == 0) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", num(r, "id"));
            m.put("name", r.get("name"));
            m.put("company", r.get("company"));
            m.put("country", r.get("country"));
            m.put("ownerUserId", r.get("ownerUserId"));
            m.put("dealt", bool(r.get("dealt")));
            m.put("mails", num(r, "mails"));
            m.put("whatsapp", num(r, "was"));
            m.put("messages", msgs);
            Object last = r.get("lastContact");
            m.put("lastContact", last instanceof LocalDateTime l ? l.format(DAY) : last == null ? null : String.valueOf(last).substring(0, Math.min(10, String.valueOf(last).length())));
            top.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("days", days);
        out.put("scope", scope);
        out.put("totals", totals);
        out.put("recency", recency);
        out.put("byCountry", dist(channelCustomerOverviewMapper.byCountry(users)));
        out.put("bySource", dist(channelCustomerOverviewMapper.bySource(users)));
        out.put("byLevel", dist(channelCustomerOverviewMapper.byLevel(users)));
        out.put("monthly", monthly);
        out.put("funnel", funnel);
        out.put("ended", ended);
        out.put("topActive", top);
        return out;
    }

    private static List<Map<String, Object>> dist(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            out.add(Map.of("key", String.valueOf(r.get("k")), "count", num(r, "n"), "dealt", num(r, "dealt")));
        }
        return out;
    }

    private static long num(Map<String, Object> r, String k) {
        Object v = r == null ? null : r.get(k);
        return v instanceof Number n ? n.longValue() : v instanceof Boolean b ? (b ? 1 : 0) : 0;
    }

    private static double dec(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v instanceof Number n ? Math.round(n.doubleValue() * 100) / 100.0 : 0;
    }

    private static boolean bool(Object v) {
        return v instanceof Boolean b ? b : v instanceof Number n && n.intValue() == 1;
    }

}
