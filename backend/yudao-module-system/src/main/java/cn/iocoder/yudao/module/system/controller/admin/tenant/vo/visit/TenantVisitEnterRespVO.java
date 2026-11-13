package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.visit;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 进入客户后台 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantVisitEnterRespVO {

    @Schema(description = "客户（租户）编号；之后的请求放在 visit-tenant-id 请求头里", example = "122")
    private Long tenantId;

    @Schema(description = "客户名称", example = "Acme 外贸")
    private String tenantName;

    @Schema(description = "本次进入的有效期，到期后需要重新进入")
    private LocalDateTime expireTime;

}
