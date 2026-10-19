package cn.iocoder.yudao.module.channel.controller.admin.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Schema(description = "管理后台 - 邮箱账号新增/修改 Request VO")
@Data
public class ChannelMailAccountSaveReqVO {

    @Schema(description = "编号，修改时必填", example = "1")
    private Long id;

    @Schema(description = "服务商：gmail、outlook、qq、exmail、163、126、263、aliyun、custom", example = "custom")
    private String provider;

    @Schema(description = "邮箱地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "sales@example.com")
    @NotBlank(message = "邮箱地址不能为空")
    @Email(message = "邮箱地址格式不正确")
    private String email;

    @Schema(description = "发件人显示名称", example = "Sales Team")
    @Size(max = 64)
    private String displayName;

    @Schema(description = "IMAP 服务器", requiredMode = Schema.RequiredMode.REQUIRED, example = "imap.example.com")
    @NotBlank(message = "IMAP 服务器不能为空")
    private String imapHost;

    @Schema(description = "IMAP 端口", requiredMode = Schema.RequiredMode.REQUIRED, example = "993")
    @NotNull(message = "IMAP 端口不能为空")
    @Min(1) @Max(65535)
    private Integer imapPort;

    @Schema(description = "IMAP 是否 SSL", example = "true")
    private Boolean imapSsl;

    @Schema(description = "SMTP 服务器", requiredMode = Schema.RequiredMode.REQUIRED, example = "smtp.example.com")
    @NotBlank(message = "SMTP 服务器不能为空")
    private String smtpHost;

    @Schema(description = "SMTP 端口", requiredMode = Schema.RequiredMode.REQUIRED, example = "465")
    @NotNull(message = "SMTP 端口不能为空")
    @Min(1) @Max(65535)
    private Integer smtpPort;

    @Schema(description = "SMTP 是否 SSL", example = "true")
    private Boolean smtpSsl;

    @Schema(description = "登录用户名，不填则用邮箱地址", example = "sales@example.com")
    private String username;

    @Schema(description = "密码或授权码；修改时不填表示不变", example = "app-password")
    private String password;

    @Schema(description = "是否启用自动收信", example = "true")
    private Boolean enabled;

}
