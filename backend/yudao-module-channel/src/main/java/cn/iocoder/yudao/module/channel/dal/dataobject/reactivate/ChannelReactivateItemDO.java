package cn.iocoder.yudao.module.channel.dal.dataobject.reactivate;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 客户唤醒任务中的一位客户及其待发内容
 */
@TableName("channel_reactivate_item")
@KeySequence("channel_reactivate_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelReactivateItemDO extends TenantBaseDO {

    // 状态：generating 生成中、draft 待确认、approved 已确认待发送、sent 已发送、failed 失败、skipped 已跳过
    public static final String STATUS_GENERATING = "generating";
    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_SKIPPED = "skipped";

    @TableId
    private Long id;
    /**
     * 任务编号
     */
    private Long taskId;
    /**
     * 客户编号
     */
    private Long customerId;
    /**
     * 客户名称
     */
    private String customerName;
    /**
     * 客户负责人
     */
    private Long ownerUserId;
    /**
     * 上次联系时间
     */
    private LocalDateTime lastContactTime;
    /**
     * 发送渠道：mail、whatsapp
     */
    private String channel;
    /**
     * 收件人：邮箱或 WhatsApp 号码
     */
    private String recipient;
    /**
     * 发送使用的邮箱或 WhatsApp 账号
     */
    private Long accountId;
    /**
     * 邮件主题
     */
    private String subject;
    /**
     * 正文；WhatsApp 为模板预览
     */
    private String content;
    /**
     * 状态：generating、draft、approved、sent、failed、skipped
     */
    private String status;
    /**
     * 失败或跳过原因
     */
    private String reasonDetail;
    /**
     * 生成消耗的智点
     */
    private Long credits;
    /**
     * 发送时间
     */
    private LocalDateTime sentTime;
    /**
     * 发出的邮件编号或 WhatsApp 消息编号
     */
    private Long sentRefId;
    /**
     * 是否已就客户回复发过通知
     */
    private Boolean replyNotified;

}
