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

    @Schema(description = "平台：whatsapp、messenger、instagram")
    private String platform;

    @Schema(description = "接入方式：manual 手动填写令牌、oauth Facebook 登录授权")
    private String authType;
    private String wabaId;
    private String displayPhone;
    private String verifyToken;
    private String apiBase;
    private String apiVersion;
    private Boolean enabled;
    private LocalDateTime lastWebhookTime;

    @Schema(description = "扫码接入的连接状态：open 已连接、connecting 连接中、close 已断开")
    private String connState;
    @Schema(description = "接待成员（用户编号，逗号分隔）")
    private String assignUserIds;
    private LocalDateTime createTime;

    @Schema(description = "在 Meta 后台填写的回调地址路径，需拼上你的公网域名")
    private String webhookPath;

    @Schema(description = "是否已填写访问令牌")
    private Boolean hasAccessToken;

    @Schema(description = "是否已填写应用密钥")
    private Boolean hasAppSecret;

}
