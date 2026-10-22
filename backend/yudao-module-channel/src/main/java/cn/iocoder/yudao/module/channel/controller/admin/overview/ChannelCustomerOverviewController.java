package cn.iocoder.yudao.module.channel.controller.admin.overview;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.service.overview.ChannelCustomerOverviewService;
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

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客户概览")
@RestController
@RequestMapping("/channel/customer-overview")
@Validated
public class ChannelCustomerOverviewController {

    @Resource
    private ChannelCustomerOverviewService channelCustomerOverviewService;

    @GetMapping("/get")
    @Operation(summary = "客户概览：总量、成交率、活跃度、分布、商机阶段、最活跃客户（按数据权限）")
    @Parameter(name = "days", description = "「新增客户」「最活跃」的统计天数，默认 30", example = "30")
    @Parameter(name = "userId", description = "只看某个业务员；须在本人数据权限范围内")
    @PreAuthorize("@ss.hasPermission('channel:dashboard:query')")
    public CommonResult<Map<String, Object>> get(@RequestParam(value = "days", defaultValue = "30") Integer days,
                                                 @RequestParam(value = "userId", required = false) Long userId) {
        return success(channelCustomerOverviewService.overview(getLoginUserId(), days, userId));
    }

}
