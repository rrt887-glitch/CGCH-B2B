package cn.iocoder.yudao.module.channel.dal.dataobject.template;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 邮件模板：写邮件时一键插入标题与正文
 */
@TableName("channel_mail_template")
@KeySequence("channel_mail_template_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMailTemplateDO extends TenantBaseDO {

    /** 分类：推广邮件、通知邮件、事务邮件 */
    public static final String CATEGORY_PROMO = "promo";
    public static final String CATEGORY_NOTICE = "notice";
    public static final String CATEGORY_TRANSACTION = "transaction";
    /** 可见范围：全员、仅自己 */
    public static final String VISIBILITY_ALL = "all";
    public static final String VISIBILITY_SELF = "self";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 模板名称
     */
    private String name;
    /**
     * 分类：promo 推广邮件、notice 通知邮件、transaction 事务邮件
     */
    private String category;
    /**
     * 可见范围：all 全员、self 仅自己
     */
    private String visibility;
    /**
     * 创建人
     */
    private Long ownerUserId;
    /**
     * 邮件标题
     */
    private String subject;
    /**
     * 邮件正文（HTML）
     */
    private String html;
    /**
     * 是否启用
     */
    private Boolean enabled;
    /**
     * 是否为该分类的默认模板
     */
    private Boolean defaulted;

}
