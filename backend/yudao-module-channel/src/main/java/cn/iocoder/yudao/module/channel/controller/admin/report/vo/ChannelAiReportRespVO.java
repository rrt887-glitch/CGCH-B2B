package cn.iocoder.yudao.module.channel.controller.admin.report.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Schema(description = "管理后台 - AI 工作报告")
@Data
public class ChannelAiReportRespVO {

    private Long id;
    @Schema(description = "daily 日报、weekly 周报")
    private String periodType;
    @Schema(description = "统计开始日期 yyyy-MM-dd")
    private String periodStart;
    @Schema(description = "统计结束日期 yyyy-MM-dd")
    private String periodEnd;
    @Schema(description = "文字总结")
    private String summary;
    @Schema(description = "总结是否由大模型撰写")
    private Boolean aiGenerated;
    private Long credits;
    @Schema(description = "auto 定时、manual 手动")
    private String generateMode;
    @Schema(description = "生成时间 yyyy-MM-dd HH:mm")
    private String createTime;
    @Schema(description = "统计数据：current、previous、pendingReasons、handoffs、gaps；列表接口不返回")
    private Object stats;

}
