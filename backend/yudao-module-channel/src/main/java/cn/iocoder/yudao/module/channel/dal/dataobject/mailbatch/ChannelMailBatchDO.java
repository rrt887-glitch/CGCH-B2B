package cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 邮件群发任务：选客户、写邮件，按速率从多个邮箱轮流发出，每封带退订链接
 */
@TableName("channel_mail_batch")
@KeySequence("channel_mail_batch_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMailBatchDO extends TenantBaseDO {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_SENDING = "sending";
    public static final String STATUS_DONE = "done";
    public static final String STATUS_CANCELLED = "cancelled";

    public static final String TYPE_TEXT = "text";
    public static final String TYPE_HTML = "html";

    @TableId
    private Long id;
    /** 任务名称 */
    private String name;
    /** 邮件主题，可用 {name} {company} */
    private String subject;
    /** 邮件正文，可用 {name} {company} */
    private String content;
    /** 正文格式：text、html */
    private String contentType;
    /** 发件邮箱编号，逗号分隔 */
    private String accountIds;
    /** 每分钟最多发几封 */
    private Integer ratePerMinute;
    /** 状态：draft、sending、done、cancelled */
    private String status;
    /** 收件人数 */
    private Integer total;
    /** 下一封的发送时间 */
    private LocalDateTime nextSendTime;
    private LocalDateTime startTime;
    private LocalDateTime finishTime;
    /** 创建人 */
    private Long userId;

}
