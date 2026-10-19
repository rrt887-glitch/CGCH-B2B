package cn.iocoder.yudao.module.channel.controller.admin.mail.vo;

import lombok.Data;

@Data
public class ChannelMailAttachmentRespVO {

    private Long id;
    private Long messageId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String fileUrl;
    private Boolean inlined;

}
