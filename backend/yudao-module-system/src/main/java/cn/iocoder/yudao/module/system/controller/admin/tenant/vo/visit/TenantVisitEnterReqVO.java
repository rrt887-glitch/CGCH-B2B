package cn.iocoder.yudao.module.system.controller.admin.tenant.vo.visit;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 进入客户后台 Request VO")
@Data
public class TenantVisitEnterReqVO {

    @Schema(description = "客户（租户）编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "122")
    @NotNull(message = "请选择要进入的客户")
    private Long tenantId;

    @Schema(description = "进入原因", requiredMode = Schema.RequiredMode.REQUIRED, example = "客户反馈 WhatsApp 收不到消息")
    @NotBlank(message = "请填写进入客户后台的原因")
    @Size(max = 200, message = "原因不超过 200 字")
    private String reason;

}
