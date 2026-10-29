package cn.iocoder.yudao.module.channel.dal.dataobject.quickreply;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 快捷回复：会话里一键插入的常用话术，分全员（管理员维护）和个人（本人可见）
 */
@TableName("channel_quick_reply")
@KeySequence("channel_quick_reply_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelQuickReplyDO extends TenantBaseDO {

    public static final String SCOPE_ENTERPRISE = "enterprise";
    public static final String SCOPE_PERSONAL = "personal";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 范围：enterprise 全员可用、personal 仅自己
     */
    private String scope;
    /**
     * 个人快捷回复的所属用户
     */
    private Long ownerUserId;
    /**
     * 标题（输入 / 后按标题搜索）
     */
    private String title;
    /**
     * 内容，可含变量 {name} 客户称呼、{company} 我方公司名
     */
    private String content;
    /**
     * 排序，越小越靠前
     */
    private Integer sort;
    /**
     * 使用次数，常用的排在前面
     */
    private Integer useCount;

}
