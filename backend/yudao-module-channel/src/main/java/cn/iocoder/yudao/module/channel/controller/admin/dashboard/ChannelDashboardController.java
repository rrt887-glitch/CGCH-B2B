package cn.iocoder.yudao.module.channel.controller.admin.dashboard;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO;
import cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 数据看板")
@RestController
@RequestMapping("/channel/dashboard")
@Validated
public class ChannelDashboardController {

    @Resource
    private ChannelDashboardService channelDashboardService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardV3Service channelDashboardV3Service;

    @GetMapping("/v3")
    @Operation(summary = "看板「数据分析」：流量占比、AI 接待表现、商业转化、需要人工支持的事项")
    @PreAuthorize("@ss.hasPermission('channel:dashboard:query')")
    public CommonResult<java.util.Map<String, Object>> v3(
            @RequestParam(value = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(pattern = "yyyy-MM-dd") java.time.LocalDate endDate) {
        java.time.LocalDate end = endDate == null ? java.time.LocalDate.now() : endDate;
        java.time.LocalDate start = startDate == null ? end : startDate;
        if (start.isAfter(end) || start.isBefore(end.minusDays(366))) {
            start = end;
        }
        return success(channelDashboardV3Service.overview(getLoginUserId(), start, end));
    }

    @GetMapping("/overview")
    @Operation(summary = "看板总览：最近 N 天（含今天）与上一个同等周期")
    @Parameter(name = "days", description = "天数，默认 30，最多 366", example = "30")
    @Parameter(name = "userId", description = "只看某个业务员；须在本人数据权限范围内", example = "1")
    @PreAuthorize("@ss.hasPermission('channel:dashboard:query')")
    public CommonResult<ChannelDashboardRespVO> overview(@RequestParam(value = "days", defaultValue = "30") Integer days,
                                                         @RequestParam(value = "userId", required = false) Long userId) {
        return success(channelDashboardService.overview(getLoginUserId(), days, userId));
    }

}
