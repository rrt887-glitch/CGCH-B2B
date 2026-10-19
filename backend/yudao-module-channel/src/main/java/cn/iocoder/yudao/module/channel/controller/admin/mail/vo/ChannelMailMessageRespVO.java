package cn.iocoder.yudao.module.channel.controller.admin.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 邮件 Response VO；列表中不含正文")
@Data
public class ChannelMailMessageRespVO {

    private Long id;
    private Long accountId;
    private String folder;
    private Integer direction;
    private String messageId;
    private String threadKey;
    private String fromAddress;
    private String fromName;
    private String toAddresses;
    private String ccAddresses;
    private String subject;
    private String snippet;
    private String textBody;
    private String htmlBody;
    private LocalDateTime sentTime;
    private Boolean readStatus;
    private Boolean replied;

    @Schema(description = "关联客户编号")
    private Long customerId;

    @Schema(description = "关联客户名称")
    private String customerName;

}
