package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - WhatsApp 会话 Response VO")
@Data
public class ChannelWaConversationRespVO {

    private Long id;
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

    @Schema(description = "24 小时客服窗口是否开启；关闭时只能发模板消息")
    private Boolean windowOpen;

    @Schema(description = "窗口关闭时间")
    private LocalDateTime windowExpireTime;

}
