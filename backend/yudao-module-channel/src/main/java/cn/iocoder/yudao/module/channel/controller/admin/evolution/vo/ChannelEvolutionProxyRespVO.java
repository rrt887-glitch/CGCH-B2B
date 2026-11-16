package cn.iocoder.yudao.module.channel.controller.admin.evolution.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - WhatsApp 扫码接入的出口代理 Response VO")
@Data
public class ChannelEvolutionProxyRespVO {

    @Schema(description = "客户（租户）编号")
    private Long tenantId;
    @Schema(description = "客户名称")
    private String tenantName;
    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "代理主机")
    private String host;
    @Schema(description = "代理端口")
    private Integer port;
    @Schema(description = "协议：http、https、socks4、socks5")
    private String protocol;
    @Schema(description = "代理账号")
    private String username;
    @Schema(description = "是否已设置密码（不回传密码本身）")
    private Boolean hasPassword;
    @Schema(description = "最近一次设置到号码上的时间")
    private LocalDateTime lastAppliedTime;
    @Schema(description = "最近一次设置失败的原因")
    private String lastError;

}
