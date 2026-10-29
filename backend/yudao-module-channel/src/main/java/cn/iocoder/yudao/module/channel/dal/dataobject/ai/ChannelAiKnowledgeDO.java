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
    /** 从资料文件拆出的段落，fileId 指向资料文件 */
    public static final String TYPE_FILE = "file";

    /** 公司物料：管理员维护，所有人的会话都会用 */
    public static final String SCOPE_ENTERPRISE = "enterprise";
    /** 个人物料：业务员自己维护，只在他负责的会话里使用 */
    public static final String SCOPE_PERSONAL = "personal";
    /** 物料分类：产品、资质认证、价格政策、物流交期、售后服务、其他 */
    public static final java.util.List<String> CATEGORIES = java.util.List.of("product", "certificate", "price", "logistics", "aftersale", "other");
    public static final java.util.Map<String, String> CATEGORY_LABELS = java.util.Map.of("product", "产品", "certificate", "资质认证",
            "price", "价格政策", "logistics", "物流交期", "aftersale", "售后服务", "other", "其他");

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

    /**
     * 来源资料文件编号（type 为 file 时）
     */
    private Long fileId;
    /**
     * 范围：enterprise 公司物料（默认）、personal 个人物料
     */
    private String scope;
    /**
     * 个人物料的所属用户
     */
    private Long ownerUserId;
    /**
     * 分类：product、certificate、price、logistics、aftersale、other，可为空
     */
    private String category;

    public boolean isPersonal() {
        return SCOPE_PERSONAL.equals(scope);
    }

    /** 这条资料能否用于 userId 负责的会话：公司物料都能用，个人物料只给本人 */
    public boolean usableBy(Long userId) {
        return !isPersonal() || (ownerUserId != null && ownerUserId.equals(userId));
    }

}
