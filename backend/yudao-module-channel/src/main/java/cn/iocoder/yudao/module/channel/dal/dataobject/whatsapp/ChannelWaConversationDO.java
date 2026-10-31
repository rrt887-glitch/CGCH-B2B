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
    /**
     * 咨询意图：sales 销售咨询、after_sales 售后支持、business 商务合作、invalid 无效咨询；为空表示未分类
     */
    private String intent;
    /**
     * 是否已归档：归档后不在默认列表里显示，客户再发消息时自动取消归档
     */
    private Boolean archived;
    /**
     * 是否收藏（星标）
     */
    private Boolean starred;
    /**
     * 负责人：转交后由该业务员处理；为空表示由号码所属的业务员处理
     */
    private Long ownerUserId;
    /**
     * 备注名：业务员给客户起的名字，优先于对方昵称显示
     */
    private String remarkName;
    /**
     * 客户头像：从 WhatsApp 联系人资料下载后存到本系统的文件地址（国内打不开 WhatsApp 的图片地址）
     */
    private String avatarUrl;
    /**
     * 最近一次获取头像的时间（没有头像也记录，避免反复获取）
     */
    private LocalDateTime avatarTime;
    /**
     * 头像来源标识（WhatsApp 图片地址去掉签名参数后的摘要），头像没换就不重复下载
     */
    private String avatarKey;

    /** 实际处理人：转交给了别人就是负责人，否则是号码所属的业务员 */
    public Long handlerUserId(ChannelWaAccountDO account) {
        return ownerUserId != null ? ownerUserId : account == null ? null : account.getUserId();
    }

    public boolean isWeb() {
        return ChannelWaAccountDO.PLATFORM_WEB.equals(platform);
    }

    /** WhatsApp 联系人隐藏了手机号：waId 是「隐藏编号@lid」，不是号码 */
    public boolean isHiddenNumber() {
        return waId != null && waId.endsWith("@lid");
    }

    public boolean isWhatsApp() {
        return platform == null || ChannelWaAccountDO.PLATFORM_WHATSAPP.equals(platform);
    }

    /** 展示用的联系人：昵称，没有昵称时 WhatsApp 显示号码，其他平台显示平台名 */
    public String contactLabel() {
        if (profileName != null && !profileName.isBlank()) {
            return profileName;
        }
        if (isWhatsApp()) {
            return isHiddenNumber() ? "隐藏号码联系人" : "+" + waId;
        }
        if ("youtube".equals(platform) || "tiktok".equals(platform)) {
            return ("youtube".equals(platform) ? "YouTube" : "TikTok") + " 用户";
        }
        if (isWeb()) {
            return "官网访客 " + (waId == null ? "" : waId.substring(Math.max(0, waId.length() - 4)).toUpperCase());
        }
        return (ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(platform) ? "Instagram 用户 " : "Facebook 用户 ")
                + (waId == null ? "" : waId.substring(Math.max(0, waId.length() - 4)));
    }

    /** 平台中文名，用于提示和备注 */
    public String platformLabel() {
        if (isWhatsApp()) {
            return "WhatsApp";
        }
        if (isWeb()) {
            return "官网IM";
        }
        if ("youtube".equals(platform)) {
            return "YouTube 评论";
        }
        if ("tiktok".equals(platform)) {
            return "TikTok 评论";
        }
        return ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(platform) ? "Instagram" : "Facebook Messenger";
    }

}
