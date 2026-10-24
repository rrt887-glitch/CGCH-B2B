package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_CREDIT_INSUFFICIENT;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 智能体（v3）配置的读写：配技能、转人工、固定开场白、学习真人销售，以及「立即学习」。
 */
@Service
@Slf4j
public class ChannelAgentSettingsService {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** 一次学习最多读取的对话数 */
    private static final int MAX_THREADS = 40;
    /** 每段对话最多保留的字符数 */
    private static final int THREAD_CHARS = 1500;

    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper channelWaConversationMapper;
    @Resource
    private cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private cn.iocoder.yudao.module.system.api.user.AdminUserApi adminUserApi;

    /** 当前配置（补齐默认值）与页面需要的选项 */
    public Map<String, Object> get() {
        ChannelAiConfigDO cfg = channelAiService.getConfig();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("skills", ChannelAgentSettings.skills(cfg));
        r.put("handoff", ChannelAgentSettings.handoff(cfg));
        r.put("opening", ChannelAgentSettings.opening(cfg));
        r.put("learn", ChannelAgentSettings.learn(cfg));
        r.put("company", ChannelAgentSettings.company(cfg));
        r.put("salesStyle", StrUtil.nullToEmpty(cfg.getSalesStyle()));
        r.put("humanPauseHours", cfg.getHumanPauseHours() == null ? 12 : cfg.getHumanPauseHours());
        Map<String, Object> ws = new LinkedHashMap<>();
        ws.put("baseUrl", StrUtil.nullToEmpty(cfg.getSearchBaseUrl()));
        ws.put("hasKey", StrUtil.isNotBlank(cfg.getSearchApiKey()));
        r.put("webSearch", ws);
        List<Map<String, String>> presets = new ArrayList<>();
        ChannelAgentSettings.HANDOFF_PRESETS.forEach((k, v) -> presets.add(Map.of("key", k, "group", v[0], "text", v[1], "type", v[2])));
        r.put("presets", presets);
        r.put("askFieldOptions", ChannelAgentSettings.ASK_FIELDS);
        return r;
    }

    /** 只更新请求里带的部分；其余保持不变 */
    public void save(Map<String, Object> req) {
        ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
        boolean insert = cfg == null;
        if (insert) {
            cfg = channelAiService.getConfig();
        }
        ChannelAiConfigDO upd = new ChannelAiConfigDO();
        if (req.containsKey("skills")) {
            upd.setSkillsJson(ChannelAgentSettings.write(convert(req.get("skills"), ChannelAgentSettings.Skills.class)));
        }
        if (req.containsKey("handoff")) {
            ChannelAgentSettings.Handoff h = convert(req.get("handoff"), ChannelAgentSettings.Handoff.class);
            if (h.getCustom().size() > 20) {
                h.setCustom(new ArrayList<>(h.getCustom().subList(0, 20)));
            }
            h.getCustom().removeIf(c -> StrUtil.isBlank(c.getText()));
            if (!List.of("pause", "off", "none").contains(h.getTakeoverMode())) {
                h.setTakeoverMode("pause");
            }
            upd.setHandoffJson(ChannelAgentSettings.write(h));
        }
        if (req.containsKey("opening")) {
            ChannelAgentSettings.Opening o = convert(req.get("opening"), ChannelAgentSettings.Opening.class);
            o.setGreeting(StrUtil.maxLength(StrUtil.nullToEmpty(o.getGreeting()), 1000));
            o.setFallback(StrUtil.maxLength(StrUtil.nullToEmpty(o.getFallback()), 1000));
            upd.setOpeningJson(ChannelAgentSettings.write(o));
        }
        if (req.containsKey("learn")) {
            ChannelAgentSettings.Learn old = ChannelAgentSettings.learn(cfg);
            ChannelAgentSettings.Learn l = convert(req.get("learn"), ChannelAgentSettings.Learn.class);
            l.setDays(Math.max(1, Math.min(l.getDays(), 365)));
            l.setLastLearnTime(old.getLastLearnTime()); // 学习结果只由「立即学习」写入
            l.setLastSamples(old.getLastSamples());
            upd.setLearnJson(ChannelAgentSettings.write(l));
        }
        if (req.containsKey("company")) {
            ChannelAgentSettings.Company c = convert(req.get("company"), ChannelAgentSettings.Company.class);
            c.setName(StrUtil.maxLength(StrUtil.nullToEmpty(c.getName()).trim(), 200));
            c.setIndustry(StrUtil.maxLength(StrUtil.nullToEmpty(c.getIndustry()).trim(), 100));
            c.setWebsite(StrUtil.maxLength(StrUtil.nullToEmpty(c.getWebsite()).trim(), 300));
            c.setIntro(StrUtil.maxLength(StrUtil.nullToEmpty(c.getIntro()).trim(), 3000));
            upd.setCompanyJson(ChannelAgentSettings.write(c));
        }
        if (req.containsKey("salesStyle")) {
            upd.setSalesStyle(StrUtil.maxLength(StrUtil.nullToEmpty((String) req.get("salesStyle")), 3000));
        }
        if (req.get("webSearch") instanceof Map<?, ?> ws) {
            upd.setSearchBaseUrl(StrUtil.maxLength(StrUtil.nullToEmpty(StrUtil.trim((String) ws.get("baseUrl"))), 255));
            if (ws.get("apiKey") instanceof String k && StrUtil.isNotBlank(k)) {
                upd.setSearchApiKey(k.trim()); // 不填表示不修改
            }
        }
        if (req.get("humanPauseHours") instanceof Number n) {
            upd.setHumanPauseHours(Math.max(0, Math.min(n.intValue(), 720)));
        }
        if (insert) {
            channelAiService.saveConfig(cfg); // 先建一条默认配置
            cfg = channelAiConfigMapper.selectCurrent();
        }
        upd.setId(cfg.getId());
        channelAiConfigMapper.updateById(upd);
    }

    private static <T> T convert(Object v, Class<T> type) {
        return v == null ? ChannelAgentSettings.read(null, type) : JSON.convertValue(v, type);
    }

    // ==================== 学习真人销售 ====================

    /**
     * 读取最近 N 天销售人工回复的对话，让大模型总结挖需策略与回复风格，写入话术要点（回复时附在系统提示里）。
     *
     * @return samples 用到的对话数、salesStyle 学到的要点
     */
    public Map<String, Object> learnNow(Long userId) {
        ChannelAiConfigDO cfg = channelAiService.getConfig();
        if (StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
            throw exception(AI_LLM_FAIL, "请先在「设置」中配置大模型");
        }
        if (!channelCreditService.hasBalance()) {
            throw exception(AI_CREDIT_INSUFFICIENT);
        }
        ChannelAgentSettings.Learn learn = ChannelAgentSettings.learn(cfg);
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, learn.getDays()));
        List<String> threads = new ArrayList<>();
        Scope sc = Scope.of(learn);
        if (sc != null && sc.empty()) {
            throw exception(AI_LLM_FAIL, "请先选择要学习的账号和渠道");
        }
        threads.addAll(waThreads(since, learn.isRepliedOnly(), sc));
        threads.addAll(mailThreads(since, learn.isRepliedOnly(), sc));
        if (threads.isEmpty()) {
            throw exception(AI_LLM_FAIL, "最近 " + learn.getDays() + " 天" + (sc == null ? "" : "所选范围内") + "没有可学习的人工回复对话");
        }
        if (threads.size() > MAX_THREADS) {
            threads = threads.subList(0, MAX_THREADS);
        }
        List<String> goals = learn.getGoals() == null || learn.getGoals().isEmpty() ? List.of("needs", "style") : learn.getGoals();
        StringBuilder want = new StringBuilder();
        if (goals.contains("needs")) {
            want.append("- 挖需策略：销售如何识别客户需求、追问哪些信息、如何推进客户说出真实诉求；\n");
        }
        if (goals.contains("style")) {
            want.append("- 回复风格：常用语气、开头与结尾的写法、回复长度、称呼与礼貌用语习惯；\n");
        }
        String system = "你是外贸销售培训师。下面是本公司真人销售与海外客户的真实对话。请总结出可以直接指导 AI 销售助理回复的要点：\n"
                + want + "要求：用中文分条输出，每条一句话，最多 15 条；只写可复用的做法，不要出现具体客户姓名、公司名、价格或联系方式。";
        String user = String.join("\n\n-----\n\n", threads);
        try {
            LlmClient.Result r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(),
                    List.of(LlmClient.Message.system(system), LlmClient.Message.user(StrUtil.maxLength(user, 40000))), 0.3);
            channelCreditService.consume("mail", 0L, userId, r.promptTokens() + r.completionTokens(), "学习真人销售");
            String style = StrUtil.maxLength(StrUtil.trim(r.content()), 3000);
            learn.setLastLearnTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            learn.setLastSamples(threads.size());
            channelAiConfigMapper.updateById(new ChannelAiConfigDO().setId(channelAiConfigMapper.selectCurrent().getId())
                    .setSalesStyle(style).setLearnJson(ChannelAgentSettings.write(learn)));
            Map<String, Object> res = new LinkedHashMap<>();
            res.put("samples", threads.size());
            res.put("salesStyle", style);
            res.put("lastLearnTime", learn.getLastLearnTime());
            return res;
        } catch (LlmClient.LlmException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
    }

    /** 「指定账号 · 指定渠道」的学习范围；null 表示全部 */
    private record Scope(Set<Long> wa, Set<Long> mail, Set<Long> waConvs, Set<String> mailThreads) {
        static Scope of(ChannelAgentSettings.Learn l) {
            if (!"some".equals(l.getScope())) {
                return null;
            }
            Set<Long> waConvs = new HashSet<>();
            Set<String> mailThreads = new HashSet<>();
            for (String k : l.getConversations() == null ? List.<String>of() : l.getConversations()) {
                if (k == null) {
                    continue;
                }
                if (k.startsWith("wa:") && StrUtil.isNumeric(k.substring(3))) {
                    waConvs.add(Long.parseLong(k.substring(3)));
                } else if (k.startsWith("mail:") && k.length() > 5) {
                    mailThreads.add(k.substring(5));
                }
            }
            return new Scope(new HashSet<>(l.getWaAccountIds() == null ? List.of() : l.getWaAccountIds()),
                    new HashSet<>(l.getMailAccountIds() == null ? List.of() : l.getMailAccountIds()), waConvs, mailThreads);
        }

        boolean picked() {
            return !waConvs.isEmpty() || !mailThreads.isEmpty();
        }

        boolean empty() {
            return wa.isEmpty() && mail.isEmpty();
        }
    }

    /** WhatsApp / Facebook / Instagram / 官网IM：有人工发出消息的会话 */
    private List<String> waThreads(LocalDateTime since, boolean repliedOnly, Scope sc) {
        if (sc != null && (sc.wa().isEmpty() || sc.picked() && sc.waConvs().isEmpty())) {
            return List.of(); // 指定了会话时，只学习指定的会话
        }
        List<ChannelWaMessageDO> manual = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                .inIfPresent(ChannelWaMessageDO::getAccountId, sc == null ? null : sc.wa())
                .inIfPresent(ChannelWaMessageDO::getConversationId, sc == null || sc.waConvs().isEmpty() ? null : sc.waConvs())
                .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_OUT)
                .and(w -> w.eq(ChannelWaMessageDO::getAiGenerated, false).or().isNull(ChannelWaMessageDO::getAiGenerated))
                .ge(ChannelWaMessageDO::getMessageTime, since)
                .isNotNull(ChannelWaMessageDO::getTextBody)
                .orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 500"));
        List<Long> convIds = manual.stream().map(ChannelWaMessageDO::getConversationId).distinct().limit(MAX_THREADS).toList();
        List<String> out = new ArrayList<>();
        for (Long convId : convIds) {
            List<ChannelWaMessageDO> msgs = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                    .eq(ChannelWaMessageDO::getConversationId, convId).ge(ChannelWaMessageDO::getMessageTime, since)
                    .orderByAsc(ChannelWaMessageDO::getMessageTime).last("LIMIT 60"));
            if (repliedOnly && msgs.stream().noneMatch(m -> m.getDirection() == ChannelWaMessageDO.DIRECTION_IN)) {
                continue;
            }
            String t = msgs.stream().filter(m -> StrUtil.isNotBlank(m.getTextBody()))
                    .map(m -> (m.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "客户：" : Boolean.TRUE.equals(m.getAiGenerated()) ? "AI：" : "销售：")
                            + StrUtil.maxLength(m.getTextBody().trim(), 400))
                    .collect(Collectors.joining("\n"));
            if (t.contains("销售：")) {
                out.add(StrUtil.maxLength(t, THREAD_CHARS));
            }
        }
        return out;
    }

    /** 邮件：有人工发出邮件的往来 */
    private List<String> mailThreads(LocalDateTime since, boolean repliedOnly, Scope sc) {
        if (sc != null && (sc.mail().isEmpty() || sc.picked() && sc.mailThreads().isEmpty())) {
            return List.of();
        }
        List<ChannelMailMessageDO> manual = channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .inIfPresent(ChannelMailMessageDO::getAccountId, sc == null ? null : sc.mail())
                .inIfPresent(ChannelMailMessageDO::getThreadKey, sc == null || sc.mailThreads().isEmpty() ? null : sc.mailThreads())
                .eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_OUT)
                .and(w -> w.eq(ChannelMailMessageDO::getAiGenerated, false).or().isNull(ChannelMailMessageDO::getAiGenerated))
                .ge(ChannelMailMessageDO::getSentTime, since)
                .isNotNull(ChannelMailMessageDO::getThreadKey)
                .orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 300"));
        List<String> keys = manual.stream().map(ChannelMailMessageDO::getThreadKey).distinct().limit(MAX_THREADS).toList();
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            List<ChannelMailMessageDO> msgs = channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                    .eq(ChannelMailMessageDO::getThreadKey, key).orderByAsc(ChannelMailMessageDO::getSentTime).last("LIMIT 12"));
            if (repliedOnly && msgs.stream().noneMatch(m -> m.getDirection() == ChannelMailMessageDO.DIRECTION_IN)) {
                continue;
            }
            String t = msgs.stream()
                    .map(m -> (m.getDirection() == ChannelMailMessageDO.DIRECTION_IN ? "客户：" : Boolean.TRUE.equals(m.getAiGenerated()) ? "AI：" : "销售：")
                            + StrUtil.maxLength(StrUtil.blankToDefault(StrUtil.trim(m.getTextBody()), StrUtil.nullToEmpty(m.getSnippet())), 500))
                    .collect(Collectors.joining("\n"));
            if (t.contains("销售：")) {
                out.add(StrUtil.maxLength(t, THREAD_CHARS));
            }
        }
        return out;
    }

    // ==================== 学习范围：账号、渠道、会话 ====================

    /** 可选的账号（成员）及其渠道账号 */
    public List<Map<String, Object>> learnScopeOptions() {
        List<Map<String, Object>> channels = new ArrayList<>();
        for (ChannelWaAccountDO a : channelWaAccountMapper.selectList()) {
            String p = StrUtil.blankToDefault(a.getPlatform(), ChannelWaAccountDO.PLATFORM_WHATSAPP);
            channels.add(channelOption("wa", a.getId(), a.getUserId(), p, StrUtil.blankToDefault(a.getDisplayPhone(), a.getName())));
        }
        for (ChannelMailAccountDO a : channelMailAccountMapper.selectList()) {
            channels.add(channelOption("mail", a.getId(), a.getUserId(), "mail", a.getEmail()));
        }
        Set<Long> userIds = new LinkedHashSet<>();
        channels.forEach(c -> userIds.add((Long) c.get("userId")));
        userIds.remove(null);
        Map<Long, AdminUserRespDTO> users = adminUserApi.getUserMap(userIds);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Long uid : userIds) {
            Map<String, Object> u = new LinkedHashMap<>();
            u.put("userId", uid);
            u.put("name", users.containsKey(uid) ? users.get(uid).getNickname() : "成员 " + uid);
            u.put("channels", channels.stream().filter(c -> uid.equals(c.get("userId"))).toList());
            out.add(u);
        }
        return out;
    }

    private static Map<String, Object> channelOption(String type, Long id, Long userId, String platform, String label) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("id", id);
        m.put("userId", userId);
        m.put("platform", platform);
        m.put("label", StrUtil.blankToDefault(label, "#" + id));
        return m;
    }

    /** 选中渠道下最近 N 天有人工回复的会话 */
    public List<Map<String, Object>> learnConversations(Collection<Long> waIds, Collection<Long> mailIds, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 365)));
        List<Map<String, Object>> out = new ArrayList<>();
        if (waIds != null && !waIds.isEmpty()) {
            List<Long> convIds = channelWaMessageMapper.selectList(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                            .in(ChannelWaMessageDO::getAccountId, waIds)
                            .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_OUT)
                            .and(w -> w.eq(ChannelWaMessageDO::getAiGenerated, false).or().isNull(ChannelWaMessageDO::getAiGenerated))
                            .ge(ChannelWaMessageDO::getMessageTime, since)
                            .orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 500"))
                    .stream().map(ChannelWaMessageDO::getConversationId).distinct().limit(100).toList();
            if (!convIds.isEmpty()) {
                for (ChannelWaConversationDO c : channelWaConversationMapper.selectByIds(convIds)) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("key", "wa:" + c.getId());
                    m.put("channel", c.platformLabel());
                    m.put("title", c.contactLabel());
                    m.put("preview", c.getLastMessagePreview());
                    m.put("time", c.getLastMessageTime());
                    out.add(m);
                }
            }
        }
        if (mailIds != null && !mailIds.isEmpty()) {
            Map<String, ChannelMailMessageDO> threads = new LinkedHashMap<>();
            channelMailMessageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                            .in(ChannelMailMessageDO::getAccountId, mailIds)
                            .eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_OUT)
                            .and(w -> w.eq(ChannelMailMessageDO::getAiGenerated, false).or().isNull(ChannelMailMessageDO::getAiGenerated))
                            .ge(ChannelMailMessageDO::getSentTime, since).isNotNull(ChannelMailMessageDO::getThreadKey)
                            .orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 300"))
                    .forEach(m -> threads.putIfAbsent(m.getThreadKey(), m));
            threads.values().stream().limit(100).forEach(mm -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", "mail:" + mm.getThreadKey());
                m.put("channel", "邮件");
                m.put("title", StrUtil.blankToDefault(mm.getSubject(), "(无主题)"));
                m.put("preview", StrUtil.blankToDefault(mm.getToAddresses(), ""));
                m.put("time", mm.getSentTime());
                out.add(m);
            });
        }
        out.sort((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time"))));
        return out;
    }

}
