package cn.iocoder.yudao.module.channel.service.dashboard;

import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Day;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Totals;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.UserRow;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.dashboard.ChannelDashboardMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Scope;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.DASHBOARD_USER_FORBIDDEN;

/**
 * 数据看板：询盘量、回复率、AI 处理量、智点消耗、线索转化，按天和按业务员汇总。
 */
@Service
public class ChannelDashboardService {

    private static final int SOURCE_MAIL = 10;
    private static final int SOURCE_WHATSAPP = 11;

    @Resource
    private ChannelDashboardMapper channelDashboardMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private PermissionApi permissionApi;
    @Resource
    private AdminUserApi adminUserApi;

    /** 统计口径：业务员范围（null 为不限）及其名下的邮箱 / WhatsApp 账号 */
    private record Filter(Set<Long> userIds, List<Long> mailAccountIds, List<Long> waAccountIds) {
        boolean all() {
            return userIds == null;
        }
    }

    /**
     * 按角色的数据权限决定能看谁：全部 / 指定部门（及下级）/ 仅本人。
     * 业务员只能看到自己的数据；主管可看本部门；管理员可看全部，也可按业务员筛选。
     */
    public Scope resolveScope(Long loginUserId, Long userId) {
        Scope scope = new Scope();
        DeptDataPermissionRespDTO perm = permissionApi.getDeptDataPermission(loginUserId);
        if (perm != null && Boolean.TRUE.equals(perm.getAll())) {
            scope.setAll(true);
        } else {
            Set<Long> visible = new TreeSet<>();
            if (perm != null && perm.getDeptIds() != null && !perm.getDeptIds().isEmpty()) {
                adminUserApi.getUserListByDeptIds(perm.getDeptIds()).stream().map(AdminUserRespDTO::getId).forEach(visible::add);
            }
            visible.add(loginUserId); // 自己的数据总能看
            scope.setUserIds(new ArrayList<>(visible));
        }
        if (userId != null && !scope.isAll() && !scope.getUserIds().contains(userId)) {
            throw exception(DASHBOARD_USER_FORBIDDEN);
        }
        scope.setUserId(userId);
        return scope;
    }

    private Filter filter(Scope scope, Map<Long, Long> mailOwner, Map<Long, Long> waOwner) {
        Set<Long> users = scope.getUserId() != null ? Set.of(scope.getUserId())
                : scope.isAll() ? null : new HashSet<>(scope.getUserIds());
        if (users == null) {
            return new Filter(null, null, null);
        }
        return new Filter(users, ownedBy(mailOwner, users), ownedBy(waOwner, users));
    }

    private static List<Long> ownedBy(Map<Long, Long> owner, Set<Long> users) {
        List<Long> ids = new ArrayList<>();
        owner.forEach((acc, uid) -> {
            if (uid != null && users.contains(uid)) {
                ids.add(acc);
            }
        });
        if (ids.isEmpty()) {
            ids.add(-1L); // 没有名下账号：IN (-1) 不会命中任何数据
        }
        return ids;
    }

    public ChannelDashboardRespVO overview(Long loginUserId, int days, Long userId) {
        days = Math.max(1, Math.min(days, 366));
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();
        LocalDateTime prevStart = start.minusDays(days);

        ChannelDashboardRespVO vo = new ChannelDashboardRespVO();
        vo.setDays(days);
        vo.setStartDate(startDate.toString());

        Map<String, Day> daily = new LinkedHashMap<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            Day day = new Day();
            day.setDate(d.toString());
            daily.put(day.getDate(), day);
        }
        Map<Long, Long> mailOwner = new HashMap<>();
        channelMailAccountMapper.selectList().forEach(a -> mailOwner.put(a.getId(), a.getUserId()));
        Map<Long, Long> waOwner = new HashMap<>();
        channelWaAccountMapper.selectList().forEach(a -> waOwner.put(a.getId(), a.getUserId()));
        Map<Long, UserRow> users = new LinkedHashMap<>();
        Scope scope = resolveScope(loginUserId, userId);
        vo.setScope(scope);
        Filter f = filter(scope, mailOwner, waOwner);

        vo.setCurrent(totals(start, end, f, daily, users, mailOwner, waOwner, vo.getPendingReasons()));
        vo.setPrevious(totals(prevStart, start, f, null, null, mailOwner, waOwner, null));
        vo.setDaily(new ArrayList<>(daily.values()));
        business(vo, start, end, f, users);
        List<UserRow> rows = new ArrayList<>(users.values());
        rows.sort((a, b) -> Long.compare(b.getMailIn() + b.getWaIn(), a.getMailIn() + a.getWaIn()));
        vo.setUsers(rows);
        return vo;
    }

    /** 经营分析：按业务员的线索、客户、跟进、订单；按平台的询盘；按国家的线索、客户、成交 */
    private void business(ChannelDashboardRespVO vo, LocalDateTime start, LocalDateTime end, Filter f, Map<Long, UserRow> users) {
        Set<Long> ids = f.userIds();
        for (Map<String, Object> r : channelDashboardMapper.cluesByOwner(start, end, ids)) {
            UserRow u = user(users, longVal(r, "u"));
            if (u != null) {
                u.setClues(num(r, "n"));
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.customersByOwner(start, end, ids)) {
            UserRow u = user(users, longVal(r, "u"));
            if (u != null) {
                u.setCustomers(num(r, "n"));
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.followUpsByCreator(start, end, ids)) {
            Long uid = null;
            try {
                uid = Long.valueOf(str(r, "u"));
            } catch (Exception ignored) {
                // 系统写入的记录没有记录人
            }
            UserRow u = user(users, uid);
            if (u != null) {
                u.setFollowUps(num(r, "n"));
            }
        }
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        long orders = 0;
        for (Map<String, Object> r : channelDashboardMapper.ordersByOwner(start, end, ids)) {
            java.math.BigDecimal amount = r.get("amount") == null ? java.math.BigDecimal.ZERO : new java.math.BigDecimal(String.valueOf(r.get("amount")));
            orders += num(r, "n");
            total = total.add(amount);
            UserRow u = user(users, longVal(r, "u"));
            if (u != null) {
                u.setOrders(num(r, "n"));
                u.setOrderAmount(amount);
            }
        }
        vo.setOrders(orders);
        vo.setOrderAmount(total.setScale(2, java.math.RoundingMode.HALF_UP));
        for (Map<String, Object> r : channelDashboardMapper.platformInquiries(start, end, f.waAccountIds())) {
            vo.getPlatforms().put(str(r, "p"), num(r, "n"));
        }
        vo.getPlatforms().put("mail", vo.getCurrent() == null ? 0 : vo.getCurrent().getMailIn());
        Map<String, ChannelDashboardRespVO.CountryRow> countries = new LinkedHashMap<>();
        java.util.function.Function<String, ChannelDashboardRespVO.CountryRow> row = c -> countries.computeIfAbsent(c, k -> {
            ChannelDashboardRespVO.CountryRow x = new ChannelDashboardRespVO.CountryRow();
            x.setCountryCode(k);
            return x;
        });
        for (Map<String, Object> r : channelDashboardMapper.cluesByCountry(start, end, ids)) {
            row.apply(str(r, "c")).setClues(num(r, "n"));
        }
        for (Map<String, Object> r : channelDashboardMapper.customersByCountry(start, end, ids)) {
            ChannelDashboardRespVO.CountryRow x = row.apply(str(r, "c"));
            x.setCustomers(num(r, "n"));
            x.setDeals(num(r, "deal"));
        }
        List<ChannelDashboardRespVO.CountryRow> list = new ArrayList<>(countries.values());
        list.sort((a, b) -> Long.compare(b.getClues() + b.getCustomers(), a.getClues() + a.getCustomers()));
        vo.setCountries(list);
    }

    /** 全租户范围的汇总（AI 工作报告使用），pendingReasons 可为 null */
    public Totals totalsAll(LocalDateTime start, LocalDateTime end, Map<String, Long> pendingReasons) {
        Map<Long, Long> mailOwner = new HashMap<>();
        channelMailAccountMapper.selectList().forEach(a -> mailOwner.put(a.getId(), a.getUserId()));
        Map<Long, Long> waOwner = new HashMap<>();
        channelWaAccountMapper.selectList().forEach(a -> waOwner.put(a.getId(), a.getUserId()));
        return totals(start, end, new Filter(null, null, null), null, null, mailOwner, waOwner, pendingReasons);
    }

    /** daily / users / pendingReasons 为 null 时只算总数（用于上期对比） */
    private Totals totals(LocalDateTime start, LocalDateTime end, Filter f, Map<String, Day> daily, Map<Long, UserRow> users,
                          Map<Long, Long> mailOwner, Map<Long, Long> waOwner, Map<String, Long> pendingReasons) {
        Totals t = new Totals();
        for (Map<String, Object> r : channelDashboardMapper.mailStats(start, end, f.mailAccountIds())) {
            long in = num(r, "inbound"), replied = num(r, "replied"), out = num(r, "outbound"), ai = num(r, "ai");
            t.setMailIn(t.getMailIn() + in);
            t.setMailReplied(t.getMailReplied() + replied);
            t.setMailOut(t.getMailOut() + out);
            t.setMailAiOut(t.getMailAiOut() + ai);
            Day d = daily == null ? null : daily.get(str(r, "d"));
            if (d != null) {
                d.setMailIn(d.getMailIn() + in);
                d.setAiOut(d.getAiOut() + ai);
                d.setHumanOut(d.getHumanOut() + out - ai);
            }
            UserRow u = user(users, mailOwner.get(longVal(r, "a")));
            if (u != null) {
                u.setMailIn(u.getMailIn() + in);
                u.setOut(u.getOut() + out);
                u.setAiOut(u.getAiOut() + ai);
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.waStats(start, end, f.waAccountIds())) {
            long in = num(r, "inbound"), out = num(r, "outbound"), ai = num(r, "ai");
            t.setWaIn(t.getWaIn() + in);
            t.setWaOut(t.getWaOut() + out);
            t.setWaAiOut(t.getWaAiOut() + ai);
            Day d = daily == null ? null : daily.get(str(r, "d"));
            if (d != null) {
                d.setWaIn(d.getWaIn() + in);
                d.setAiOut(d.getAiOut() + ai);
                d.setHumanOut(d.getHumanOut() + out - ai);
            }
            UserRow u = user(users, waOwner.get(longVal(r, "a")));
            if (u != null) {
                u.setWaIn(u.getWaIn() + in);
                u.setOut(u.getOut() + out);
                u.setAiOut(u.getAiOut() + ai);
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.waConversations(start, end, f.waAccountIds())) {
            t.setWaConversations(t.getWaConversations() + 1);
            Object firstIn = r.get("first_in"), lastOut = r.get("last_out");
            if (firstIn != null && lastOut != null && String.valueOf(lastOut).compareTo(String.valueOf(firstIn)) >= 0) {
                t.setWaRepliedConversations(t.getWaRepliedConversations() + 1);
            }
        }
        List<Map<String, Object>> drafts = f.all() ? channelDashboardMapper.drafts(start, end) : new ArrayList<>();
        if (!f.all()) {
            drafts.addAll(channelDashboardMapper.mailDrafts(start, end, f.mailAccountIds()));
            drafts.addAll(channelDashboardMapper.waDrafts(start, end, f.waAccountIds()));
        }
        for (Map<String, Object> r : drafts) {
            long n = num(r, "n");
            String status = str(r, "s"), reason = str(r, "r");
            t.setAiDrafts(t.getAiDrafts() + n);
            if (ChannelAiDraftDO.STATUS_SENT.equals(status)) {
                t.setAiSent(t.getAiSent() + n);
            } else if (ChannelAiDraftDO.STATUS_PENDING.equals(status)) {
                t.setAiPending(t.getAiPending() + n);
                if (pendingReasons != null) {
                    pendingReasons.merge(reason == null ? "other" : reason, n, Long::sum);
                }
            } else if (ChannelAiDraftDO.STATUS_DISCARDED.equals(status)) {
                t.setAiDiscarded(t.getAiDiscarded() + n);
            }
            if (ChannelAiDraftDO.REASON_HANDOFF.equals(reason)) {
                t.setAiHandoff(t.getAiHandoff() + n);
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.credits(start, end, f.userIds())) {
            long n = num(r, "n");
            t.setCreditsConsumed(t.getCreditsConsumed() + n);
            Day d = daily == null ? null : daily.get(str(r, "d"));
            if (d != null) {
                d.setCredits(d.getCredits() + n);
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.clues(start, end, f.userIds())) {
            long n = num(r, "n");
            t.setCluesCreated(t.getCluesCreated() + n);
            t.setCluesConverted(t.getCluesConverted() + num(r, "converted"));
            long source = longVal(r, "s") == null ? -1 : longVal(r, "s");
            if (source == SOURCE_MAIL) {
                t.setCluesFromMail(t.getCluesFromMail() + n);
            } else if (source == SOURCE_WHATSAPP) {
                t.setCluesFromWhatsApp(t.getCluesFromWhatsApp() + n);
            }
        }
        Long customers = channelDashboardMapper.customers(start, end, f.userIds());
        t.setCustomersCreated(customers == null ? 0 : customers);

        t.setInquiries(t.getMailIn() + t.getWaConversations());
        t.setReplyRate(ratio(t.getMailReplied() + t.getWaRepliedConversations(), t.getInquiries()));
        t.setAiShare(ratio(t.getMailAiOut() + t.getWaAiOut(), t.getMailOut() + t.getWaOut()));
        return t;
    }

    private static UserRow user(Map<Long, UserRow> users, Long userId) {
        if (users == null || userId == null) {
            return null;
        }
        return users.computeIfAbsent(userId, id -> {
            UserRow u = new UserRow();
            u.setUserId(id);
            return u;
        });
    }

    private static double ratio(long a, long b) {
        return b == 0 ? 0 : Math.round(a * 1000.0 / b) / 1000.0;
    }

    private static long num(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v instanceof Number n ? n.longValue() : 0;
    }

    private static Long longVal(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v instanceof Number n ? n.longValue() : null;
    }

    private static String str(Map<String, Object> r, String k) {
        Object v = r.get(k);
        return v == null ? null : String.valueOf(v);
    }

}
