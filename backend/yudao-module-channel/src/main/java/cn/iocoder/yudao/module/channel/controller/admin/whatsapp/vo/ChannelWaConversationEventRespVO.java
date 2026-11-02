package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 会话内部备注 / 动态 Response VO")
@Data
public class ChannelWaConversationEventRespVO {

    private Long id;
    private Long conversationId;
    @Schema(description = "类型：note 内部备注、activity 会话动态")
    private String kind;
    private String content;
    private Long userId;
    @Schema(description = "操作人昵称；系统动态为空")
    private String userName;
    private LocalDateTime createTime;

}
