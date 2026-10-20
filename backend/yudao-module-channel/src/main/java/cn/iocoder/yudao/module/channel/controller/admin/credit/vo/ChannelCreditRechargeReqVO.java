package cn.iocoder.yudao.module.channel.controller.admin.credit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 智点充值/调整；正数增加，负数扣减")
@Data
public class ChannelCreditRechargeReqVO {

    @Schema(description = "数量，正数增加、负数扣减", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
    @NotNull(message = "数量不能为空")
    private Long amount;

    @Schema(description = "备注", example = "线下转账充值")
    @Size(max = 200)
    private String remark;

}
