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
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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

    public ChannelDashboardRespVO overview(int days) {
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

        vo.setCurrent(totals(start, end, daily, users, mailOwner, waOwner, vo.getPendingReasons()));
        vo.setPrevious(totals(prevStart, start, null, null, mailOwner, waOwner, null));
        vo.setDaily(new ArrayList<>(daily.values()));
        List<UserRow> rows = new ArrayList<>(users.values());
        rows.sort((a, b) -> Long.compare(b.getMailIn() + b.getWaIn(), a.getMailIn() + a.getWaIn()));
        vo.setUsers(rows);
        return vo;
    }

    /** daily / users / pendingReasons 为 null 时只算总数（用于上期对比） */
    private Totals totals(LocalDateTime start, LocalDateTime end, Map<String, Day> daily, Map<Long, UserRow> users,
                          Map<Long, Long> mailOwner, Map<Long, Long> waOwner, Map<String, Long> pendingReasons) {
        Totals t = new Totals();
        for (Map<String, Object> r : channelDashboardMapper.mailStats(start, end)) {
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
        for (Map<String, Object> r : channelDashboardMapper.waStats(start, end)) {
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
        for (Map<String, Object> r : channelDashboardMapper.waConversations(start, end)) {
            t.setWaConversations(t.getWaConversations() + 1);
            Object firstIn = r.get("first_in"), lastOut = r.get("last_out");
            if (firstIn != null && lastOut != null && String.valueOf(lastOut).compareTo(String.valueOf(firstIn)) >= 0) {
                t.setWaRepliedConversations(t.getWaRepliedConversations() + 1);
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.drafts(start, end)) {
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
        for (Map<String, Object> r : channelDashboardMapper.credits(start, end)) {
            long n = num(r, "n");
            t.setCreditsConsumed(t.getCreditsConsumed() + n);
            Day d = daily == null ? null : daily.get(str(r, "d"));
            if (d != null) {
                d.setCredits(d.getCredits() + n);
            }
        }
        for (Map<String, Object> r : channelDashboardMapper.clues(start, end)) {
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
        Long customers = channelDashboardMapper.customers(start, end);
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
