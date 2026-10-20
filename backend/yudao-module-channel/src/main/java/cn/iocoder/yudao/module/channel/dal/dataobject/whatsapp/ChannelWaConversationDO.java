package cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.time.LocalDateTime;

/**
 * WhatsApp 会话：一个业务号码与一位客户之间的聊天
 */
@TableName("channel_wa_conversation")
@KeySequence("channel_wa_conversation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChannelWaConversationDO extends TenantBaseDO {

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
     * 客户的 WhatsApp 号码（纯数字，含国家码）
     */
    private String waId;
    /**
     * 客户在 WhatsApp 上的昵称
     */
    private String profileName;
    /**
     * 关联的 CRM 客户编号
     */
    private Long customerId;
    /**
     * 关联的 CRM 线索编号（未成为客户的询盘）
     */
    private Long clueId;
    /**
     * 最后一条消息时间
     */
    private LocalDateTime lastMessageTime;
    /**
     * 客户最后一次发来消息的时间，用于判断 24 小时客服窗口
     */
    private LocalDateTime lastInboundTime;
    /**
     * 最后一条消息摘要
     */
    private String lastMessagePreview;
    /**
     * 未读条数
     */
    private Integer unreadCount;

}
