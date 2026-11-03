package cn.iocoder.yudao.module.channel.controller.admin.mailbatch.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 新建邮件群发 Request VO")
@Data
public class ChannelMailBatchCreateReqVO {

    @NotBlank(message = "请填写任务名称")
    private String name;
    @NotBlank(message = "请填写邮件主题")
    private String subject;
    @NotBlank(message = "请填写邮件正文")
    private String content;
    @Schema(description = "正文格式：text 纯文本、html")
    private String contentType;
    @Schema(description = "CRM 客户编号（取客户邮箱）")
    private List<Long> customerIds;
    @Schema(description = "直接填写的邮箱")
    private List<String> emails;
    @NotEmpty(message = "请选择发件邮箱")
    private List<Long> accountIds;
    @Min(value = 1, message = "每分钟至少 1 封")
    @Max(value = 60, message = "每分钟最多 60 封")
    private Integer ratePerMinute;

}
