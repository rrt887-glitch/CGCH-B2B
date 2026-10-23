package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.*;

/**
 * 智能体（v3）的配置项：配技能、转人工、固定开场白、学习真人销售。
 * 以 JSON 存在 channel_ai_config 的几个文本字段里，读取时补齐默认值，新增配置项不需要改表。
 */
public final class ChannelAgentSettings {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ChannelAgentSettings() {
    }

    // ==================== 转人工 ====================

    /** 预置的转人工情形：编号 → [分组, 描述, 看板分类] */
    public static final LinkedHashMap<String, String[]> HANDOFF_PRESETS = new LinkedHashMap<>();

    static {
        HANDOFF_PRESETS.put("ask_human", new String[]{"客户诉求类", "客户明确要求真人或人工接待", "handoff"});
        HANDOFF_PRESETS.put("doubt_ai", new String[]{"客户诉求类", "客户质疑正在与机器人或 AI 交流", "handoff"});
        HANDOFF_PRESETS.put("ask_quote", new String[]{"客户诉求类", "客户明确询价或要求出具报价", "permission"});
        HANDOFF_PRESETS.put("ask_sample", new String[]{"客户诉求类", "客户要求寄送样品（仅咨询样品信息除外）", "permission"});
        HANDOFF_PRESETS.put("insult", new String[]{"情绪与投诉类", "客户侮辱、攻击", "crisis"});
        HANDOFF_PRESETS.put("unhappy", new String[]{"情绪与投诉类", "客户强烈不满或失望", "crisis"});
        HANDOFF_PRESETS.put("complaint", new String[]{"情绪与投诉类", "客户威胁或升级投诉", "crisis"});
        HANDOFF_PRESETS.put("after_sales", new String[]{"业务流程相关", "售后服务请求：已购订单的服务或履约问题（包括样品的物流跟进）", "other"});
        HANDOFF_PRESETS.put("personal", new String[]{"业务流程相关", "客户为个人采购", "other"});
        HANDOFF_PRESETS.put("out_of_scope", new String[]{"业务流程相关", "客户咨询的产品或服务，超出经营范围", "missing"});
    }

    /** 默认关闭的预置项 */
    public static final Set<String> DEFAULT_OFF = Set.of("ask_quote", "ask_sample");

    /** 看板「需要支持」的分类 */
    public static final Set<String> SUPPORT_TYPES = Set.of("handoff", "missing", "permission", "crisis", "compliance", "other");

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Handoff {
        /** 按客户意图转人工是否启用 */
        private boolean intentEnabled = true;
        /** 预置情形的开关；未出现的编号视为开启 */
        private Map<String, Boolean> presets = new LinkedHashMap<>();
        /** 自定义转人工项（自然语言描述），最多 20 条，靠前的优先 */
        private List<Custom> custom = new ArrayList<>();
        /** 按客户条件转人工（老客户等），命中的客户 AI 只起草不发送 */
        private List<Condition> conditions = new ArrayList<>();
        /**
         * 人工介入时 AI 托管怎么变：pause 暂停若干小时（默认，时长见 humanPauseHours）、off 关闭该客户的 AI 托管、none 不变
         */
        private String takeoverMode = "pause";

        /** 预置项是否开启；询价、寄样默认关闭（交给「预估报价」技能回答），其余默认开启 */
        public boolean presetOn(String key) {
            Boolean v = presets.get(key);
            return v != null ? v : !DEFAULT_OFF.contains(key);
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Custom {
        private String text;
        /** 看板分类，默认 handoff */
        private String type = "handoff";
        private boolean enabled = true;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Condition {
        private String name;
        /** level 客户等级、deal 已成交、source 客户来源 */
        private String field;
        /** 取值：等级 1～4、来源字典值；deal 时忽略 */
        private List<Integer> values = new ArrayList<>();
        private boolean enabled = true;
    }

    // ==================== 配技能 ====================

    /** 主动追问的客户信息 */
    public static final List<String> ASK_FIELDS = List.of("客户类型", "需求产品", "采购时间", "联系人姓名", "进出口经验",
            "所在地区", "需求地址", "行业经验", "公司名称", "职位", "公司网站");

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Skills {
        /** 自动背调：新线索建档后自动做公司背调 */
        private boolean autoResearch = false;
        /** 预估报价：允许基于知识库回答价格与起订量；关闭时询价一律转人工 */
        private boolean quote = true;
        /** 智能跟进：AI 发出的邮件客户迟迟未回复时，自动多轮跟进 */
        private boolean followUp = false;
        /** 每隔多少小时跟进一次 */
        private int followUpHours = 48;
        /** 最多跟进几轮 */
        private int followUpRounds = 2;
        /** 会话时主动追问的信息 */
        private List<String> askFields = new ArrayList<>(List.of("需求产品", "采购时间", "公司名称"));
    }

    // ==================== 固定开场白 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Opening {
        /** 新客户第一次来信时先发开场问候 */
        private boolean enabled = false;
        private String greeting = "";
        /** AI 不能回复（转人工等）时发给客户的兜底话术；为空则不发 */
        private String fallback = "";
    }

    // ==================== 学习真人销售 ====================

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Learn {
        private boolean enabled = true;
        /** 学习最近多少天的人工对话 */
        private int days = 30;
        /** 学习目标：needs 挖需策略、style 回复风格 */
        private List<String> goals = new ArrayList<>(List.of("needs", "style"));
        /** 只学习有客户回复的对话 */
        private boolean repliedOnly = true;
        /** 最近一次学习时间、样本数 */
        private String lastLearnTime;
        private int lastSamples;
    }

    // ==================== 读写 ====================

    public static <T> T read(String json, Class<T> type) {
        try {
            return StrUtil.isBlank(json) ? type.getDeclaredConstructor().newInstance() : JSON.readValue(json, type);
        } catch (Exception e) {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public static String write(Object o) {
        try {
            return JSON.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Skills skills(ChannelAiConfigDO c) {
        return read(c == null ? null : c.getSkillsJson(), Skills.class);
    }

    public static Handoff handoff(ChannelAiConfigDO c) {
        return read(c == null ? null : c.getHandoffJson(), Handoff.class);
    }

    public static Opening opening(ChannelAiConfigDO c) {
        return read(c == null ? null : c.getOpeningJson(), Opening.class);
    }

    public static Learn learn(ChannelAiConfigDO c) {
        return read(c == null ? null : c.getLearnJson(), Learn.class);
    }

}
