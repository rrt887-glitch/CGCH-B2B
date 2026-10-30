package cn.iocoder.yudao.module.channel.controller.admin.timeline;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.service.timeline.ChannelCustomerTimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 客户沟通记录")
@RestController
@RequestMapping("/channel/customer-timeline")
public class ChannelCustomerTimelineController {

    @Resource
    private ChannelCustomerTimelineService channelCustomerTimelineService;

    @GetMapping("/list")
    @Operation(summary = "客户在各渠道的沟通记录（即时消息 + 邮件），按时间倒序；before 为毫秒时间戳，用于加载更早的")
    @PreAuthorize("@ss.hasPermission('crm:customer:query')")
    public CommonResult<ChannelCustomerTimelineService.Page> list(@RequestParam("customerId") Long customerId,
                                                                  @RequestParam(value = "before", required = false) Long before,
                                                                  @RequestParam(value = "limit", required = false) Integer limit) {
        LocalDateTime b = before == null ? null : LocalDateTime.ofInstant(Instant.ofEpochMilli(before), ZoneId.systemDefault());
        return success(channelCustomerTimelineService.list(customerId, b, limit));
    }

}
