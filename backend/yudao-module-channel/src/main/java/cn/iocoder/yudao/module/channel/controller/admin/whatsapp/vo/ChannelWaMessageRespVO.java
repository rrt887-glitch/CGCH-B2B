package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelWaMessageRespVO {

    private Long id;
    private Long conversationId;
    private Integer direction;
    private String waMessageId;
    private String type;
    private String textBody;
    private String mediaId;
    private String mimeType;
    private String fileName;
    /** 群聊里发这条消息的成员（号码或隐藏编号@lid） */
    private String senderWaId;
    /** 群聊里发这条消息的成员昵称 */
    private String senderName;
    private String fileUrl;
    private Long fileSize;
    private String templateName;
    private String status;
    private String errorMessage;
    private LocalDateTime messageTime;

    private Boolean aiGenerated;

}
