package cn.iocoder.yudao.module.channel.controller.admin.credit.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - 智点概览")
@Data
public class ChannelCreditSummaryRespVO {

    @Schema(description = "当前余额")
    private Long balance;

    @Schema(description = "累计获得")
    private Long totalIn;

    @Schema(description = "累计消耗")
    private Long totalOut;

    @Schema(description = "本月消耗")
    private Long monthConsumed;

    @Schema(description = "本月按渠道的消耗")
    private Map<String, Long> monthByChannel;

    @Schema(description = "本月按业务员的消耗，键为用户编号")
    private Map<Long, Long> monthByUser;

    @Schema(description = "每智点对应的 token 数")
    private Integer tokensPerCredit;

}
