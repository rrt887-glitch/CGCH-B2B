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

@Tag(name = "管理后台 - 数据看板")
@RestController
@RequestMapping("/channel/dashboard")
@Validated
public class ChannelDashboardController {

    @Resource
    private ChannelDashboardService channelDashboardService;

    @GetMapping("/overview")
    @Operation(summary = "看板总览：最近 N 天（含今天）与上一个同等周期")
    @Parameter(name = "days", description = "天数，默认 30，最多 366", example = "30")
    @PreAuthorize("@ss.hasPermission('channel:dashboard:query')")
    public CommonResult<ChannelDashboardRespVO> overview(@RequestParam(value = "days", defaultValue = "30") Integer days) {
        return success(channelDashboardService.overview(days));
    }

}
