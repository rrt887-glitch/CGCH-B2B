package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 生成的回复：起草模式下的草稿、自动回复被拦下的草稿，以及已自动发出的记录
 */
@TableName("channel_ai_draft")
@KeySequence("channel_ai_draft_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiDraftDO extends TenantBaseDO {

    public static final String CHANNEL_MAIL = "mail";
    public static final String CHANNEL_WHATSAPP = "whatsapp";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_DISCARDED = "discarded";

    /** 为什么没有自动发出 */
    public static final String REASON_DRAFT_MODE = "draft_mode";
    public static final String REASON_HANDOFF = "handoff";
    public static final String REASON_FORBIDDEN = "forbidden";
    public static final String REASON_HUMAN_ACTIVE = "human_active";
    public static final String REASON_NOT_HOSTED = "not_hosted";
    public static final String REASON_WINDOW_CLOSED = "window_closed";
    public static final String REASON_SEND_FAILED = "send_failed";
    public static final String REASON_MANUAL = "manual";
    public static final String REASON_NO_CREDIT = "no_credit";
    /** 命中「根据客户条件」转人工（如老客户） */
    public static final String REASON_CONDITION = "condition";
    /** 智能跟进：客户迟迟未回复时 AI 自动发出的跟进 */
    public static final String REASON_FOLLOW_UP = "follow_up";
    /** 工作时间设置为「AI 只起草」 */
    public static final String REASON_WORK_HOURS = "work_hours";
    /** 非工作时间设置为「AI 只起草」 */
    public static final String REASON_OFF_HOURS = "off_hours";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 渠道：mail、whatsapp
     */
    private String channel;
    /**
     * 关联对象：邮件为收到的邮件编号，WhatsApp 为会话编号
     */
    private Long refId;
    /**
     * 回复内容
     */
    private String content;
    /**
     * 状态：pending 待处理、sent 已发送、discarded 已放弃
     */
    private String status;
    /**
     * 未自动发送的原因，见 REASON_*
     */
    private String reason;
    /**
     * 原因补充说明，例如命中的违禁词
     */
    private String reasonDetail;
    /**
     * 本次消耗的输入 token
     */
    private Integer promptTokens;
    /**
     * 本次消耗的输出 token
     */
    private Integer completionTokens;
    /**
     * 本次扣除的智点
     */
    private Long credits;
    /**
     * 使用的模型
     */
    private String model;
    /**
     * 本次回复命中的知识条目数（不含企业介绍）；为 0 表示知识库里没有相关资料
     */
    private Integer knowledgeHits;
    /**
     * 客户本次的问题（截取），用于工作报告里的「知识库缺口」
     */
    private String question;
    /**
     * 需要人工支持的类型（看板「需要支持」分类）：handoff 已转人工、missing 信息缺失、permission 权限决策、
     * crisis 客情危机、compliance 合规风险、other 其他
     */
    private String supportType;

}
