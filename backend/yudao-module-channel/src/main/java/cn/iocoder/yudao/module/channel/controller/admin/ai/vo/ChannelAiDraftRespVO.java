package cn.iocoder.yudao.module.channel.controller.admin.ai.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChannelAiDraftRespVO {

    private Long id;
    private String channel;
    private Long refId;
    private String content;
    private String status;
    private String reason;
    private String reasonDetail;
    private Integer promptTokens;
    private Integer completionTokens;
    private String model;
    private Long credits;
    private LocalDateTime createTime;

}
