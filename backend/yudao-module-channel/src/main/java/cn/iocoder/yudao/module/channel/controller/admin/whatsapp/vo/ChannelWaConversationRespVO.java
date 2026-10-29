package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - WhatsApp 会话 Response VO")
@Data
public class ChannelWaConversationRespVO {

    private Long id;
    @Schema(description = "平台：whatsapp、messenger、instagram")
    private String platform;

    private Long accountId;
    private String waId;
    private String profileName;
    private Long customerId;
    private String customerName;
    private Long clueId;
    private String clueName;
    private LocalDateTime lastMessageTime;
    private LocalDateTime lastInboundTime;
    private String lastMessagePreview;
    private Integer unreadCount;
    private Boolean archived;
    private Boolean starred;
    @Schema(description = "负责人：转交后的处理人；为空表示号码所属业务员")
    private Long ownerUserId;
    @Schema(description = "备注名")
    private String remarkName;
    @Schema(description = "客户等级（crm_customer_level 字典值）")
    private Integer customerLevel;

    @Schema(description = "24 小时客服窗口是否开启；关闭时只能发模板消息")
    private Boolean windowOpen;

    @Schema(description = "窗口关闭时间")
    private LocalDateTime windowExpireTime;

}
