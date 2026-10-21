package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

/**
 * 成交案例：从已成交客户的沟通过程中总结出的经验，AI 回复同类询盘时参考（不会透露原客户的名称和价格）
 */
@TableName("channel_ai_case")
@KeySequence("channel_ai_case_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiCaseDO extends TenantBaseDO {

    // 来源：ai 由 AI 根据往来记录生成、manual 手动录入
    public static final String SOURCE_AI = "ai";
    public static final String SOURCE_MANUAL = "manual";

    @TableId
    private Long id;
    /**
     * 案例标题
     */
    private String title;
    /**
     * 来源客户（仅内部查看，不会提供给 AI 回复）
     */
    private Long customerId;
    /**
     * 来源客户名称（仅内部查看）
     */
    private String customerName;
    /**
     * 客户行业或类型，例如：澳洲家电分销商
     */
    private String industry;
    /**
     * 成交产品
     */
    private String product;
    /**
     * 客户所在国家
     */
    private String countryCode;
    /**
     * 背景与过程概述
     */
    private String summary;
    /**
     * 成交关键点（JSON 字符串数组）
     */
    private String keyPoints;
    /**
     * 客户顾虑及我方回应（JSON 字符串数组）
     */
    private String objections;
    /**
     * 有效话术（JSON 字符串数组）
     */
    private String phrases;
    /**
     * 是否供 AI 参考（AI 生成的案例默认关闭，需人工确认后开启）
     */
    private Boolean enabled;
    /**
     * 来源：ai、manual
     */
    private String source;
    /**
     * 生成消耗的智点
     */
    private Long credits;
    /**
     * 创建人
     */
    private Long userId;
    /**
     * 向量（JSON 数组），用于语义匹配；不对外返回
     */
    @JsonIgnore
    private String embedding;
    /**
     * 生成向量所用的模型
     */
    private String embeddingModel;

}
