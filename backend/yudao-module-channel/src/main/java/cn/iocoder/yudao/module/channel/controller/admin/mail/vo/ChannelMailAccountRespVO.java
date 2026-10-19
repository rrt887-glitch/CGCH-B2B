package cn.iocoder.yudao.module.channel.controller.admin.mail.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 邮箱账号 Response VO（不含密码）")
@Data
public class ChannelMailAccountRespVO {

    private Long id;
    private Long userId;
    private String provider;
    private String email;
    private String displayName;
    private String imapHost;
    private Integer imapPort;
    private Boolean imapSsl;
    private String smtpHost;
    private Integer smtpPort;
    private Boolean smtpSsl;
    private String username;
    private Boolean enabled;

    @Schema(description = "最近同步时间")
    private LocalDateTime lastSyncTime;

    @Schema(description = "最近同步失败原因，为空表示正常")
    private String lastSyncError;

    @Schema(description = "未回复的收件数")
    private Long unrepliedCount;

    private LocalDateTime createTime;

}
