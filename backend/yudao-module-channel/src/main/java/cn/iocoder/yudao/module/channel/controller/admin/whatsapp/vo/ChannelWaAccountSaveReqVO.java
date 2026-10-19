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

    @Schema(description = "Phone number ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123456789012345")
    @NotBlank(message = "Phone number ID 不能为空")
    private String phoneNumberId;

    @Schema(description = "WhatsApp Business Account ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "987654321098765")
    @NotBlank(message = "WhatsApp Business Account ID 不能为空")
    private String wabaId;

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
