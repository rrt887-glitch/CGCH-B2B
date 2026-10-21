package cn.iocoder.yudao.module.channel.service.worktime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelWorkTimeDO;
import cn.iocoder.yudao.module.channel.dal.mysql.worktime.ChannelWorkTimeMapper;
import jakarta.annotation.Resource;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.WORK_TIME_INVALID;

/**
 * 工作时间：判断「现在」是否在公司的上班时间内，并据此决定 AI 怎么处理新消息。
 */
@Service
public class ChannelWorkTimeService {

    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final String[] WEEK = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    @Resource
    private ChannelWorkTimeMapper channelWorkTimeMapper;

    /** 「1 分钟」对应的秒数，仅用于自动化测试缩短等待（默认 60） */
    @Value("${cgch.ai.takeover-minute-seconds:60}")
    private int minuteSeconds;

    /** 每周某一天的安排 */
    @Data
    public static class Day {
        /** 1=周一 … 7=周日 */
        private int day;
        private boolean enabled;
        /** 时间段，逗号分隔：09:00-12:00,13:30-18:00 */
        private String ranges;
    }

    // ==================== 配置 ====================

    public ChannelWorkTimeDO getConfig() {
        ChannelWorkTimeDO c = channelWorkTimeMapper.selectCurrent();
        return c != null ? c : defaults();
    }

    static ChannelWorkTimeDO defaults() {
        List<Day> days = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            Day d = new Day();
            d.setDay(i);
            d.setEnabled(i <= 5);
            d.setRanges("09:00-12:00,13:30-18:00");
            days.add(d);
        }
        return ChannelWorkTimeDO.builder().enabled(false).timezone("Asia/Shanghai").schedule(JsonUtils.toJsonString(days))
                .holidays("[]").extraWorkdays("[]").inHoursPolicy(ChannelWorkTimeDO.IN_HUMAN_FIRST).takeoverMinutes(30)
                .offHoursPolicy(ChannelWorkTimeDO.OFF_AI).build();
    }

    public void save(ChannelWorkTimeDO req) {
        try {
            ZoneId.of(StrUtil.blankToDefault(req.getTimezone(), "Asia/Shanghai"));
        } catch (Exception e) {
            throw exception(WORK_TIME_INVALID, "时区不正确：" + req.getTimezone());
        }
        List<Day> days = parseDays(req.getSchedule());
        for (Day d : days) {
            if (d.getDay() < 1 || d.getDay() > 7) {
                throw exception(WORK_TIME_INVALID, "星期只能是 1~7");
            }
            if (d.isEnabled()) {
                List<LocalTime[]> r = ranges(d.getRanges());
                if (r.isEmpty()) {
                    throw exception(WORK_TIME_INVALID, WEEK[d.getDay()] + "已勾选上班，请填写时间段");
                }
            }
        }
        dates(req.getHolidays());
        dates(req.getExtraWorkdays());
        if (!List.of(ChannelWorkTimeDO.IN_HUMAN_FIRST, ChannelWorkTimeDO.IN_AI, ChannelWorkTimeDO.IN_DRAFT).contains(req.getInHoursPolicy())) {
            req.setInHoursPolicy(ChannelWorkTimeDO.IN_HUMAN_FIRST);
        }
        if (!List.of(ChannelWorkTimeDO.OFF_AI, ChannelWorkTimeDO.OFF_DRAFT).contains(req.getOffHoursPolicy())) {
            req.setOffHoursPolicy(ChannelWorkTimeDO.OFF_AI);
        }
        if (req.getTakeoverMinutes() == null || req.getTakeoverMinutes() < 0 || req.getTakeoverMinutes() > 1440) {
            throw exception(WORK_TIME_INVALID, "接手等待时间需在 0~1440 分钟之间");
        }
        req.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
        req.setSchedule(JsonUtils.toJsonString(days));
        // 存成 yyyy-MM-dd 字符串（直接序列化 LocalDate 会变成 [2026,10,21] 数组）
        req.setHolidays(JsonUtils.toJsonString(dates(req.getHolidays()).stream().map(LocalDate::toString).toList()));
        req.setExtraWorkdays(JsonUtils.toJsonString(dates(req.getExtraWorkdays()).stream().map(LocalDate::toString).toList()));
        ChannelWorkTimeDO old = channelWorkTimeMapper.selectCurrent();
        if (old == null) {
            req.setId(null);
            channelWorkTimeMapper.insert(req);
        } else {
            req.setId(old.getId());
            channelWorkTimeMapper.updateById(req);
        }
    }

    // ==================== 判断 ====================

    private static List<Day> parseDays(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        try {
            List<Day> list = JsonUtils.parseArray(json, Day.class);
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            throw exception(WORK_TIME_INVALID, "每周安排格式不正确");
        }
    }

    static List<LocalTime[]> ranges(String s) {
        List<LocalTime[]> out = new ArrayList<>();
        for (String part : StrUtil.split(StrUtil.nullToEmpty(s), ',')) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            String[] se = part.split("-");
            try {
                LocalTime a = LocalTime.parse(se[0].trim(), HM);
                LocalTime b = "24:00".equals(se[1].trim()) ? LocalTime.MAX : LocalTime.parse(se[1].trim(), HM);
                if (!b.isAfter(a)) {
                    throw new IllegalArgumentException();
                }
                out.add(new LocalTime[]{a, b});
            } catch (Exception e) {
                throw exception(WORK_TIME_INVALID, "时间段格式不正确：" + part + "（例如 09:00-12:00）");
            }
        }
        return out;
    }

    private static Set<LocalDate> dates(String json) {
        Set<LocalDate> set = new TreeSet<>();
        if (StrUtil.isBlank(json)) {
            return set;
        }
        try {
            for (String s : JsonUtils.parseArray(json, String.class)) {
                if (StrUtil.isNotBlank(s)) {
                    set.add(LocalDate.parse(s.trim()));
                }
            }
        } catch (Exception e) {
            throw exception(WORK_TIME_INVALID, "日期格式不正确，应为 yyyy-MM-dd");
        }
        return set;
    }

    /** 某一天的上班时间段：节假日为空；调休上班日按周一 */
    private static List<LocalTime[]> rangesOn(ChannelWorkTimeDO c, LocalDate date) {
        if (dates(c.getHolidays()).contains(date)) {
            return List.of();
        }
        int dow = dates(c.getExtraWorkdays()).contains(date) ? 1 : date.getDayOfWeek().getValue();
        for (Day d : parseDays(c.getSchedule())) {
            if (d.getDay() == dow) {
                return d.isEnabled() ? ranges(d.getRanges()) : List.of();
            }
        }
        return List.of();
    }

    public static boolean isWorkTime(ChannelWorkTimeDO c, ZonedDateTime now) {
        LocalTime t = now.toLocalTime();
        return rangesOn(c, now.toLocalDate()).stream().anyMatch(r -> !t.isBefore(r[0]) && t.isBefore(r[1]));
    }

    /** 下一次「上班 / 下班」切换的时间，两周内找不到返回 null */
    static ZonedDateTime nextChange(ChannelWorkTimeDO c, ZonedDateTime now) {
        boolean in = isWorkTime(c, now);
        List<ZonedDateTime> points = new ArrayList<>();
        for (int i = 0; i <= 14; i++) {
            LocalDate d = now.toLocalDate().plusDays(i);
            for (LocalTime[] r : rangesOn(c, d)) {
                points.add(d.atTime(r[0]).atZone(now.getZone()));
                points.add((r[1].equals(LocalTime.MAX) ? d.plusDays(1).atStartOfDay() : d.atTime(r[1])).atZone(now.getZone()));
            }
        }
        return points.stream().sorted().filter(p -> p.isAfter(now) && isWorkTime(c, p) != in).findFirst().orElse(null);
    }

    private static ZoneId zone(ChannelWorkTimeDO c) {
        try {
            return ZoneId.of(StrUtil.blankToDefault(c.getTimezone(), "Asia/Shanghai"));
        } catch (Exception e) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    /** 当前状态，供页面展示 */
    public Map<String, Object> status() {
        ChannelWorkTimeDO c = getConfig();
        ZonedDateTime now = ZonedDateTime.now(zone(c));
        Map<String, Object> m = new LinkedHashMap<>();
        boolean enabled = Boolean.TRUE.equals(c.getEnabled());
        boolean in = isWorkTime(c, now);
        m.put("enabled", enabled);
        m.put("timezone", zone(c).getId());
        m.put("localTime", now.format(TIME) + " " + WEEK[now.getDayOfWeek().getValue()]);
        m.put("inWorkTime", in);
        ZonedDateTime next = nextChange(c, now);
        m.put("nextChange", next == null ? null : next.format(TIME) + " " + WEEK[next.getDayOfWeek().getValue()]);
        m.put("policy", !enabled ? "未启用工作时间，按数字员工的回复模式全天处理"
                : in ? inHoursText(c) : (ChannelWorkTimeDO.OFF_DRAFT.equals(c.getOffHoursPolicy())
                ? "现在是非工作时间：AI 只起草，等业务员上班后确认发送" : "现在是非工作时间：AI 自动接待新消息"));
        return m;
    }

    private static String inHoursText(ChannelWorkTimeDO c) {
        return switch (StrUtil.nullToEmpty(c.getInHoursPolicy())) {
            case ChannelWorkTimeDO.IN_AI -> "现在是工作时间：AI 立即回复";
            case ChannelWorkTimeDO.IN_DRAFT -> "现在是工作时间：AI 只起草，由业务员发送";
            default -> "现在是工作时间：业务员优先，" + c.getTakeoverMinutes() + " 分钟内无人回复由 AI 接手";
        };
    }

    // ==================== 对 AI 的影响 ====================

    /**
     * 本次收到消息时 AI 应怎么做。
     *
     * @param action    process 正常处理；draft 只起草；defer 等待业务员，deferSeconds 后再看
     * @param offHours  是否非工作时间（用于提示 AI 说明业务员稍后回复）
     * @param reason    draft 时的原因
     * @param localTime 公司当地时间，提示用
     */
    public record Decision(String action, boolean offHours, String reason, long deferSeconds, String localTime,
                           String nextWorkTime) {
        public static final String PROCESS = "process";
        public static final String DRAFT = "draft";
        public static final String DEFER = "defer";
    }

    public Decision decide() {
        ChannelWorkTimeDO c = getConfig();
        if (!Boolean.TRUE.equals(c.getEnabled())) {
            return new Decision(Decision.PROCESS, false, null, 0, null, null);
        }
        ZonedDateTime now = ZonedDateTime.now(zone(c));
        String local = now.format(TIME) + " " + WEEK[now.getDayOfWeek().getValue()];
        if (!isWorkTime(c, now)) {
            ZonedDateTime next = nextChange(c, now);
            String nextText = next == null ? null : next.format(TIME) + " " + WEEK[next.getDayOfWeek().getValue()];
            return ChannelWorkTimeDO.OFF_DRAFT.equals(c.getOffHoursPolicy())
                    ? new Decision(Decision.DRAFT, true, "off_hours", 0, local, nextText)
                    : new Decision(Decision.PROCESS, true, null, 0, local, nextText);
        }
        if (ChannelWorkTimeDO.IN_DRAFT.equals(c.getInHoursPolicy())) {
            return new Decision(Decision.DRAFT, false, "work_hours", 0, local, null);
        }
        if (ChannelWorkTimeDO.IN_HUMAN_FIRST.equals(c.getInHoursPolicy()) && c.getTakeoverMinutes() != null && c.getTakeoverMinutes() > 0) {
            return new Decision(Decision.DEFER, false, null, (long) c.getTakeoverMinutes() * minuteSeconds, local, null);
        }
        return new Decision(Decision.PROCESS, false, null, 0, local, null);
    }

}
