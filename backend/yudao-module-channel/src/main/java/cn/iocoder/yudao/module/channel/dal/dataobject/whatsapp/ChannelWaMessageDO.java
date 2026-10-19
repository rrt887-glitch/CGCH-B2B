package cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * WhatsApp 消息
 */
@TableName("channel_wa_message")
@KeySequence("channel_wa_message_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelWaMessageDO extends TenantBaseDO {

    public static final int DIRECTION_IN = 1;
    public static final int DIRECTION_OUT = 2;

    /** 发出消息的状态，按 Meta 回调推进 */
    public static final String STATUS_RECEIVED = "received";
    public static final String STATUS_SENT = "sent";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_READ = "read";
    public static final String STATUS_FAILED = "failed";

    /**
     * 编号
     */
    @TableId
    private Long id;
    /**
     * 业务号码编号
     */
    private Long accountId;
    /**
     * 会话编号
     */
    private Long conversationId;
    /**
     * 方向：1 收到，2 发出
     */
    private Integer direction;
    /**
     * Meta 消息编号（wamid）
     */
    private String waMessageId;
    /**
     * 类型：text、image、document、audio、video、sticker、location、template、interactive 等
     */
    private String type;
    /**
     * 文本内容、媒体说明或模板预览
     */
    private String textBody;
    /**
     * 媒体编号，可通过 Graph API 下载
     */
    private String mediaId;
    /**
     * 媒体类型
     */
    private String mimeType;
    /**
     * 文件名（document 类型）
     */
    private String fileName;
    /**
     * 已保存到本地文件存储的地址；收到的媒体在首次查看时下载
     */
    private String fileUrl;
    /**
     * 文件大小，字节
     */
    private Long fileSize;
    /**
     * 模板名称（template 类型）
     */
    private String templateName;
    /**
     * 状态：received、sent、delivered、read、failed
     */
    private String status;
    /**
     * 发送失败原因
     */
    private String errorMessage;
    /**
     * 消息时间
     */
    private LocalDateTime messageTime;

}
