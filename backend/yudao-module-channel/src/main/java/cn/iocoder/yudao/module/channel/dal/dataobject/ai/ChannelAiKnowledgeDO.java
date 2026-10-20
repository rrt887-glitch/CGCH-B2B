package cn.iocoder.yudao.module.channel.dal.dataobject.ai;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 知识库条目：企业介绍、产品资料、常见问答
 */
@TableName("channel_ai_knowledge")
@KeySequence("channel_ai_knowledge_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelAiKnowledgeDO extends TenantBaseDO {

    public static final String TYPE_COMPANY = "company";
    public static final String TYPE_PRODUCT = "product";
    public static final String TYPE_FAQ = "faq";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 类型：company 企业介绍、product 产品资料、faq 常见问答
     */
    private String type;
    /**
     * 标题；常见问答为问题
     */
    private String title;
    /**
     * 内容；常见问答为答案
     */
    private String content;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 排序
     */
    private Integer sort;
    /**
     * 标题+内容的向量（JSON 数组），用于语义检索；不对外返回
     */
    @JsonIgnore
    private String embedding;
    /**
     * 生成向量所用的模型；与当前配置的向量模型不一致时视为未建索引
     */
    private String embeddingModel;

}
