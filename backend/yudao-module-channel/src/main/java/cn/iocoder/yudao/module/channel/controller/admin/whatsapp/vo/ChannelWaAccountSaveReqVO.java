package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - WhatsApp 号码新增/修改 Request VO")
@Data
public class ChannelWaAccountSaveReqVO {

    @Schema(description = "编号，修改时必填", example = "1")
    private Long id;

    @Schema(description = "显示名称", example = "销售一部")
    @Size(max = 64)
    private String name;

    @Schema(description = "平台账号编号：WhatsApp 为 Phone number ID，Messenger 为 Page ID，Instagram 为专业账号编号",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789012345")
    @NotBlank(message = "账号编号不能为空")
    private String phoneNumberId;

    @Schema(description = "WhatsApp Business Account ID（仅 WhatsApp 必填）", example = "987654321098765")
    private String wabaId;

    @Schema(description = "平台：whatsapp（默认）、messenger（Facebook 主页私信）、instagram；创建后不能修改", example = "whatsapp")
    private String platform;

    @Schema(description = "访问令牌；修改时不填表示不变")
    private String accessToken;

    @Schema(description = "应用密钥（App Secret）；修改时不填表示不变")
    private String appSecret;

    @Schema(description = "Webhook 验证令牌；不填自动生成")
    private String verifyToken;

    @Schema(description = "Graph API 地址，一般不用改", example = "https://graph.facebook.com")
    private String apiBase;

    @Schema(description = "Graph API 版本", example = "v24.0")
    private String apiVersion;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

}
