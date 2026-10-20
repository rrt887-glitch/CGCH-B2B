package cn.iocoder.yudao.module.channel.controller.admin.credit.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Data
@EqualsAndHashCode(callSuper = true)
public class ChannelCreditLedgerPageReqVO extends PageParam {

    @Schema(description = "类型：recharge、consume、adjust")
    private String type;

    @Schema(description = "渠道：mail、whatsapp")
    private String channel;

    @Schema(description = "业务员编号")
    private Long userId;

    @Schema(description = "时间范围")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
