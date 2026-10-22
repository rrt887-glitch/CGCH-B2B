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
     * 平台：whatsapp（默认）、messenger、instagram，与所属账号一致
     */
    private String platform;
    /**
     * 客户编号：WhatsApp 为号码（纯数字，含国家码）；Messenger / Instagram 为平台分配的用户编号（PSID / IGSID）
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

    public boolean isWhatsApp() {
        return platform == null || ChannelWaAccountDO.PLATFORM_WHATSAPP.equals(platform);
    }

    /** 展示用的联系人：昵称，没有昵称时 WhatsApp 显示号码，其他平台显示平台名 */
    public String contactLabel() {
        if (profileName != null && !profileName.isBlank()) {
            return profileName;
        }
        if (isWhatsApp()) {
            return "+" + waId;
        }
        return (ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(platform) ? "Instagram 用户 " : "Facebook 用户 ")
                + (waId == null ? "" : waId.substring(Math.max(0, waId.length() - 4)));
    }

    /** 平台中文名，用于提示和备注 */
    public String platformLabel() {
        if (isWhatsApp()) {
            return "WhatsApp";
        }
        return ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(platform) ? "Instagram" : "Facebook Messenger";
    }

}
