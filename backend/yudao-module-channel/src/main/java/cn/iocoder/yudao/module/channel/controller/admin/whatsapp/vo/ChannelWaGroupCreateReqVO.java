package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 新建 WhatsApp 群聊 Request VO")
@Data
public class ChannelWaGroupCreateReqVO {

    @Schema(description = "业务号码编号（扫码接入的号码）", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "请选择用于建群的号码")
    private Long accountId;

    @Schema(description = "群名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "迪拜客户群")
    @NotBlank(message = "请填写群名称")
    @Size(max = 60, message = "群名称不能超过 60 个字符")
    private String subject;

    @Schema(description = "成员号码（含国家码，可带 + 和空格）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "请至少填写一个成员号码")
    private List<String> participants;

    @Schema(description = "建群后要发的第一句话，可不填", example = "Hi all, glad to meet you here.")
    @Size(max = 4096, message = "消息不能超过 4096 个字符")
    private String message;
}
