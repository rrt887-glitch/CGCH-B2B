package cn.iocoder.yudao.module.channel.controller.admin.worktime.vo;

import cn.iocoder.yudao.module.channel.service.worktime.ChannelWorkTimeService.Day;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - 工作时间")
@Data
public class ChannelWorkTimeVO {

    @Schema(description = "是否启用")
    private Boolean enabled;
    @Schema(description = "时区", example = "Asia/Shanghai")
    private String timezone;
    @Schema(description = "每周安排，day 1=周一 … 7=周日")
    private List<Day> schedule = new ArrayList<>();
    @Schema(description = "节假日 yyyy-MM-dd")
    private List<String> holidays = new ArrayList<>();
    @Schema(description = "调休上班日 yyyy-MM-dd（按周一的时间上班）")
    private List<String> extraWorkdays = new ArrayList<>();
    @Schema(description = "工作时间内：human_first 业务员优先、ai AI 立即回复、draft AI 只起草")
    private String inHoursPolicy;
    @Schema(description = "业务员优先时等待的分钟数")
    private Integer takeoverMinutes;
    @Schema(description = "非工作时间：ai AI 自动回复、draft AI 只起草")
    private String offHoursPolicy;

}
