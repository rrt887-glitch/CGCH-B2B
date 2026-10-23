package cn.iocoder.yudao.module.channel.dal.dataobject.mail;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 邮件（收到的与发出的）
 */
@TableName("channel_mail_message")
@KeySequence("channel_mail_message_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = {"textBody", "htmlBody"})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMailMessageDO extends TenantBaseDO {

    public static final String FOLDER_INBOX = "INBOX";
    public static final String FOLDER_SENT = "SENT";
    public static final String FOLDER_DRAFT = "DRAFT";
    public static final String FOLDER_TRASH = "TRASH";

    public static final int DIRECTION_IN = 1;
    public static final int DIRECTION_OUT = 2;

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 邮箱账号编号
     */
    private Long accountId;
    /**
     * 文件夹：INBOX、SENT、DRAFT、TRASH
     */
    private String folder;
    /**
     * 方向：1 收到，2 发出
     */
    private Integer direction;
    /**
     * 收件箱中的 IMAP UID，发出的邮件为空
     */
    private Long imapUid;
    /**
     * 邮件头 Message-ID
     */
    private String messageId;
    /**
     * 邮件头 In-Reply-To
     */
    private String inReplyTo;
    /**
     * 会话标识：同一来回往复的邮件相同
     */
    private String threadKey;
    /**
     * 发件人地址
     */
    private String fromAddress;
    /**
     * 发件人名称
     */
    private String fromName;
    /**
     * 收件人，逗号分隔
     */
    private String toAddresses;
    /**
     * 抄送，逗号分隔
     */
    private String ccAddresses;
    /**
     * 主题
     */
    private String subject;
    /**
     * 纯文本正文
     */
    private String textBody;
    /**
     * HTML 正文
     */
    private String htmlBody;
    /**
     * 摘要，列表展示用
     */
    private String snippet;
    /**
     * 发送时间
     */
    private LocalDateTime sentTime;
    /**
     * 是否已读
     */
    private Boolean readStatus;
    /**
     * 是否已回复（仅收到的邮件）
     */
    private Boolean replied;
    /**
     * 关联的 CRM 客户编号
     */
    private Long customerId;
    /**
     * 关联的 CRM 线索编号（未成为客户的询盘）
     */
    private Long clueId;
    /**
     * 是否带附件（不含正文内嵌图片）
     */
    private Boolean hasAttachments;
    /**
     * 是否由 AI 数字员工生成并发出
     */
    private Boolean aiGenerated;
    /**
     * 咨询意图（客户来信）：sales、after_sales、business、invalid；为空表示未分类
     */
    private String intent;

}
