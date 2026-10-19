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
    private String fileUrl;
    private Long fileSize;
    private String templateName;
    private String status;
    private String errorMessage;
    private LocalDateTime messageTime;

}
