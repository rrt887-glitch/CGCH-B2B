package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 数字员工配置，每个租户一条
 */
@TableName(value = "channel_ai_config", autoResultMap = true)
@KeySequence("channel_ai_config_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "llmApiKey")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiConfigDO extends TenantBaseDO {

    public static final String MODE_OFF = "off";
    public static final String MODE_DRAFT = "draft";
    public static final String MODE_AUTO = "auto";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 数字员工名称，回复时的自称
     */
    private String agentName;
    /**
     * 个性：professional 专业严谨、concise 高效简洁、friendly 温和友好、proactive 积极推进、calm 冷静专业
     */
    private String personality;
    /**
     * 默认答复语种，例如 English；无法从客户消息判断语种时使用
     */
    private String defaultLanguage;
    /**
     * 大模型接口地址（OpenAI 兼容），例如 https://api.deepseek.com/v1
     */
    private String llmBaseUrl;
    /**
     * 大模型 API Key，加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String llmApiKey;
    /**
     * 模型名称，例如 deepseek-chat、qwen-plus、gpt-4o-mini
     */
    private String llmModel;
    /**
     * 向量模型（OpenAI 兼容 /embeddings，与对话模型共用接口地址和 Key）；为空时知识库按关键词检索
     */
    private String embeddingModel;
    /**
     * 邮件回复模式：off、draft、auto
     */
    private String mailMode;
    /**
     * WhatsApp 回复模式：off、draft、auto
     */
    private String waMode;
    /**
     * 邮件签名，追加在 AI 回复末尾
     */
    private String mailSignature;
    /**
     * 业务员人工回复后，AI 暂停自动回复的小时数
     */
    private Integer humanPauseHours;
    /**
     * 补充要求，例如「报价统一用美元，不承诺交期」
     */
    private String extraInstructions;
    /**
     * 新询盘自动建 CRM 线索
     */
    private Boolean leadAutoCreate;
    /**
     * 建档时用 AI 从询盘中提取公司、国家、需求产品等（消耗智点）
     */
    private Boolean leadAiExtract;
    /**
     * 是否每天自动生成 AI 工作报告（周一另生成上周周报）
     */
    private Boolean reportAuto;
    /**
     * 关闭的通知类型，逗号分隔：handoff、takeover、new_lead、reactivate_reply、credit_low；为空表示全部开启
     */
    private String notifyMuted;

}
