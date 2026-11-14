package cn.iocoder.yudao.module.channel.controller.admin.evolution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - WhatsApp 扫码接入的出口代理")
@Data
public class ChannelEvolutionProxySaveReqVO {

    @Schema(description = "客户（租户）编号；平台在「客户管理」里配置时必填，客户自己调用时忽略", example = "122")
    private Long tenantId;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean enabled;

    @Schema(description = "代理主机", example = "proxy.example.com")
    @Size(max = 255, message = "代理主机不能超过 255 个字符")
    private String host;

    @Schema(description = "代理端口", example = "1080")
    @Min(value = 1, message = "端口需在 1~65535 之间")
    @Max(value = 65535, message = "端口需在 1~65535 之间")
    private Integer port;

    @Schema(description = "协议：http、https、socks4、socks5", example = "socks5")
    @Pattern(regexp = "^(http|https|socks4|socks5)$", message = "协议只支持 http、https、socks4、socks5")
    private String protocol;

    @Schema(description = "代理账号（可空）")
    @Size(max = 128, message = "代理账号不能超过 128 个字符")
    private String username;

    @Schema(description = "代理密码（可空；留空表示不修改原密码）")
    @Size(max = 255, message = "代理密码不能超过 255 个字符")
    private String password;

    @Schema(description = "保存后是否立即把代理应用到这家客户已有的号码上", example = "true")
    private Boolean applyNow;

    @Schema(description = "清空密码（换代理但新代理不需要密码时用）", example = "false")
    private Boolean clearPassword;

}
