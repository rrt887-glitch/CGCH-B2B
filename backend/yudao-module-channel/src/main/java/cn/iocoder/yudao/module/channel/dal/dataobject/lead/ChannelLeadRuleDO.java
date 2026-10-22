package cn.iocoder.yudao.module.channel.dal.dataobject.lead;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 留资邮箱规则：识别阿里国际站、中国制造网、官网表单等平台发来的询盘通知邮件，自动解析成线索
 */
@TableName("channel_lead_rule")
@KeySequence("channel_lead_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelLeadRuleDO extends TenantBaseDO {

    // 匹配字段：from 发件人（地址或域名）、subject 主题
    public static final String FIELD_FROM = "from";
    public static final String FIELD_SUBJECT = "subject";

    @TableId
    private Long id;
    /**
     * 平台名称，例如：阿里国际站
     */
    private String name;
    /**
     * 匹配字段：from、subject
     */
    private String matchField;
    /**
     * 匹配关键词（包含即匹配，不区分大小写），例如 alibaba.com
     */
    private String keyword;
    /**
     * 建线索时的客户来源（字典 crm_customer_source 的值）
     */
    private Integer source;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 排序
     */
    private Integer sort;

}
