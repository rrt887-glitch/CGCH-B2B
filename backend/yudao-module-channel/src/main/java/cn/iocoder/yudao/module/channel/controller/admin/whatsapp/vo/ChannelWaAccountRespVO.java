package cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - WhatsApp 号码 Response VO（不含令牌与密钥）")
@Data
public class ChannelWaAccountRespVO {

    private Long id;
    private String name;
    private String phoneNumberId;
    private String wabaId;
    private String displayPhone;
    private String verifyToken;
    private String apiBase;
    private String apiVersion;
    private Boolean enabled;
    private LocalDateTime lastWebhookTime;
    private LocalDateTime createTime;

    @Schema(description = "在 Meta 后台填写的回调地址路径，需拼上你的公网域名")
    private String webhookPath;

    @Schema(description = "是否已填写访问令牌")
    private Boolean hasAccessToken;

    @Schema(description = "是否已填写应用密钥")
    private Boolean hasAppSecret;

}
