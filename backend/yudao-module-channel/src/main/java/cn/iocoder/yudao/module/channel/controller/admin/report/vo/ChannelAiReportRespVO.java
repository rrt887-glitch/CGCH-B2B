package cn.iocoder.yudao.module.channel.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - AI 工作报告")
@Data
public class ChannelAiReportRespVO {

    private Long id;
    @Schema(description = "daily 日报、weekly 周报")
    private String periodType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    @Schema(description = "文字总结")
    private String summary;
    @Schema(description = "总结是否由大模型撰写")
    private Boolean aiGenerated;
    private Long credits;
    @Schema(description = "auto 定时、manual 手动")
    private String generateMode;
    private LocalDateTime createTime;
    @Schema(description = "统计数据：current、previous、pendingReasons、handoffs、gaps；列表接口不返回")
    private Object stats;

}
