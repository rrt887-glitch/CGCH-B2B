package cn.iocoder.yudao.module.channel.controller.admin.routing;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.routing.ChannelRoutingRuleDO;
import cn.iocoder.yudao.module.channel.service.routing.ChannelRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 消息分流规则")
@RestController
@RequestMapping("/channel/routing-rule")
@Validated
public class ChannelRoutingRuleController {

    @Resource
    private ChannelRoutingService channelRoutingService;

    @GetMapping("/list")
    @Operation(summary = "全部规则（按优先级）")
    public CommonResult<List<ChannelRoutingRuleDO>> list() {
        return success(channelRoutingService.list());
    }

    @PostMapping("/save")
    @Operation(summary = "新增或修改规则")
    @PreAuthorize("@ss.hasPermission('channel:ai:config')")
    public CommonResult<Long> save(@RequestBody ChannelRoutingRuleDO req) {
        return success(channelRoutingService.save(req));
    }

    @PutMapping("/enabled")
    @Operation(summary = "启用 / 停用")
    @PreAuthorize("@ss.hasPermission('channel:ai:config')")
    public CommonResult<Boolean> enabled(@RequestParam("id") Long id, @RequestParam("enabled") Boolean enabled) {
        channelRoutingService.setEnabled(id, enabled);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除规则")
    @PreAuthorize("@ss.hasPermission('channel:ai:config')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelRoutingService.delete(id);
        return success(true);
    }

}
