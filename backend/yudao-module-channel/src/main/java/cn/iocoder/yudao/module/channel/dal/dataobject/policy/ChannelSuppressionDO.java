package cn.iocoder.yudao.module.channel.dal.dataobject.policy;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * 退订与黑名单：名单中的收件人不会收到任何邮件或 WhatsApp 消息（手动发送也会被拦截）
 */
@TableName("channel_suppression")
@KeySequence("channel_suppression_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelSuppressionDO extends TenantBaseDO {

    // 原因：unsubscribe 客户退订、bounce 退信（地址无效）、manual 手动加入
    public static final String REASON_UNSUBSCRIBE = "unsubscribe";
    public static final String REASON_BOUNCE = "bounce";
    public static final String REASON_MANUAL = "manual";

    @TableId
    private Long id;
    /**
     * 渠道：mail、whatsapp
     */
    private String channel;
    /**
     * 邮箱（小写）、@域名（整个域名）或 WhatsApp 号码（纯数字）
     */
    private String address;
    /**
     * 原因：unsubscribe、bounce、manual
     */
    private String reason;
    /**
     * 备注（例如触发退订的原文）
     */
    private String remark;
    /**
     * 操作人（自动加入为空）
     */
    private Long userId;

}
