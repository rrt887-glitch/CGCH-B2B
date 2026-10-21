package cn.iocoder.yudao.module.channel.dal.dataobject.policy;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 发送策略：每个邮箱 / WhatsApp 号码的发送频率上限，以及退订、退信的自动处理。每个租户一条。
 */
@TableName("channel_send_policy")
@KeySequence("channel_send_policy_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSendPolicyDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 每个邮箱每小时最多发送（0 为不限）
     */
    private Integer mailHourlyLimit;
    /**
     * 每个邮箱每天最多发送（0 为不限）
     */
    private Integer mailDailyLimit;
    /**
     * 每个 WhatsApp 号码每小时最多发送（0 为不限）
     */
    private Integer waHourlyLimit;
    /**
     * 每个 WhatsApp 号码每天最多发送（0 为不限）
     */
    private Integer waDailyLimit;
    /**
     * 频率上限是否也限制业务员手动发送；默认只限制 AI 自动回复和批量发送
     */
    private Boolean applyToManual;
    /**
     * 客户回复退订（unsubscribe、STOP、退订等）时自动加入退订名单，AI 不再回复
     */
    private Boolean autoUnsubscribe;
    /**
     * 收到退信（地址不存在等）时自动把该地址加入黑名单
     */
    private Boolean autoBounce;
    /**
     * 客户唤醒等批量邮件末尾附加退订说明
     */
    private Boolean unsubscribeFooter;
    /**
     * 退订说明文字
     */
    private String footerText;

}
