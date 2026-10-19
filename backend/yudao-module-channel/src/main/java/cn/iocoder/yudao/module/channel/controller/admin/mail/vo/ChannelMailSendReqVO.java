package cn.iocoder.yudao.module.channel.controller.admin.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 发送邮件 Request VO")
@Data
public class ChannelMailSendReqVO {

    @Schema(description = "发件邮箱账号编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "发件邮箱不能为空")
    private Long accountId;

    @Schema(description = "收件人", requiredMode = Schema.RequiredMode.REQUIRED, example = "[\"buyer@acme.example\"]")
    private List<String> to;

    @Schema(description = "抄送", example = "[]")
    private List<String> cc;

    @Schema(description = "主题", example = "Re: Quotation")
    @Size(max = 500)
    private String subject;

    @Schema(description = "纯文本正文")
    private String text;

    @Schema(description = "HTML 正文，优先于纯文本")
    private String html;

    @Schema(description = "回复的邮件编号；填写后自动带上 In-Reply-To，并把原邮件标为已回复", example = "10")
    private Long replyToId;

}
