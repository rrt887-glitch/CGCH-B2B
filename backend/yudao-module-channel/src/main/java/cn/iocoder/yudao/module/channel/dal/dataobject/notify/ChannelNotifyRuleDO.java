package cn.iocoder.yudao.module.channel.dal.dataobject.notify;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 通知规则：每个账号自己设置哪类消息用什么方式通知；没有设置规则的账号默认以站内信接收全部通知
 */
@TableName("channel_notify_rule")
@KeySequence("channel_notify_rule_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelNotifyRuleDO extends TenantBaseDO {

    /** 站内信（顶部铃铛） */
    public static final String CHANNEL_SITE = "site";
    /** 邮件：用本人绑定的邮箱发到登录账号的邮箱 */
    public static final String CHANNEL_EMAIL = "email";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 所属用户
     */
    private Long userId;
    /**
     * 消息类型：handoff 转人工、takeover AI 接手、new_lead 新询盘、reactivate_reply 唤醒客户回复、credit_low 智点不足
     */
    private String type;
    /**
     * 通知方式：site 站内信、email 邮件
     */
    private String channel;
    /**
     * 是否开启
     */
    private Boolean enabled;

}
