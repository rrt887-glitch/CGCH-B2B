package cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 邮件群发的一位收件人与发送结果
 */
@TableName("channel_mail_batch_item")
@KeySequence("channel_mail_batch_item_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMailBatchItemDO extends TenantBaseDO {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_FAILED = "failed";
    public static final String STATUS_INVALID = "invalid";
    public static final String STATUS_SUPPRESSED = "suppressed";
    public static final String STATUS_CANCELLED = "cancelled";

    @TableId
    private Long id;
    private Long batchId;
    /** CRM 客户编号；直接填的邮箱为空 */
    private Long customerId;
    /** 称呼（{name}） */
    private String name;
    /** 公司（{company}） */
    private String company;
    private String email;
    /** 发件邮箱编号 */
    private Long accountId;
    /** 状态：pending、sent、failed、invalid、suppressed、cancelled */
    private String status;
    /** 失败或跳过原因 */
    private String reason;
    private LocalDateTime sentTime;
    /** 发出的邮件编号 */
    private Long sentRefId;
    /** 退订链接的令牌 */
    private String token;
    /** 是否通过这封邮件退订 */
    private Boolean unsubscribed;

}
