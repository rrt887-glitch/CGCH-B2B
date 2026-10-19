package cn.iocoder.yudao.module.channel.dal.dataobject.mail;

import cn.iocoder.yudao.framework.mybatis.core.type.EncryptTypeHandler;
import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 业务员绑定的邮箱账号（IMAP 收信 + SMTP 发信）
 */
@TableName(value = "channel_mail_account", autoResultMap = true)
@KeySequence("channel_mail_account_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, exclude = "password")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelMailAccountDO extends TenantBaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 所属用户编号
     */
    private Long userId;
    /**
     * 邮箱服务商：gmail、outlook、qq、exmail、163、126、263、aliyun、custom
     */
    private String provider;
    /**
     * 邮箱地址
     */
    private String email;
    /**
     * 发件人显示名称
     */
    private String displayName;
    /**
     * IMAP 服务器
     */
    private String imapHost;
    /**
     * IMAP 端口
     */
    private Integer imapPort;
    /**
     * IMAP 是否使用 SSL
     */
    private Boolean imapSsl;
    /**
     * SMTP 服务器
     */
    private String smtpHost;
    /**
     * SMTP 端口
     */
    private Integer smtpPort;
    /**
     * SMTP 是否使用 SSL（465）；否则尝试 STARTTLS
     */
    private Boolean smtpSsl;
    /**
     * 登录用户名，一般就是邮箱地址
     */
    private String username;
    /**
     * 登录密码或授权码，加密存储
     */
    @TableField(typeHandler = EncryptTypeHandler.class)
    private String password;
    /**
     * 是否启用自动收信
     */
    private Boolean enabled;
    /**
     * 最近一次同步时间
     */
    private LocalDateTime lastSyncTime;
    /**
     * 收件箱已同步到的最大 IMAP UID
     */
    private Long lastSyncUid;
    /**
     * 收件箱 UIDVALIDITY，变化时需要重新同步
     */
    private Long uidValidity;
    /**
     * 最近一次同步失败的原因，成功时清空
     */
    private String lastSyncError;

}
