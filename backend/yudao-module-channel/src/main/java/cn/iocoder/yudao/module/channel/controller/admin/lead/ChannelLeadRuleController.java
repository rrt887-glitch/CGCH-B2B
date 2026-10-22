package cn.iocoder.yudao.module.channel.controller.admin.lead;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.lead.ChannelLeadRuleDO;
import cn.iocoder.yudao.module.channel.service.lead.ChannelPlatformLeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 留资邮箱规则")
@RestController
@RequestMapping("/channel/lead-rule")
@Validated
public class ChannelLeadRuleController {

    @Resource
    private ChannelPlatformLeadService channelPlatformLeadService;

    @GetMapping("/list")
    @Operation(summary = "留资规则（首次使用时自动生成阿里国际站、中国制造网两条）")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:query')")
    public CommonResult<List<ChannelLeadRuleDO>> list() {
        return success(channelPlatformLeadService.rules());
    }

    @PostMapping("/save")
    @Operation(summary = "新增或修改规则")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:create')")
    public CommonResult<Long> save(@RequestBody ChannelLeadRuleDO req) {
        return success(channelPlatformLeadService.saveRule(req));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除规则")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:create')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelPlatformLeadService.deleteRule(id);
        return success(true);
    }

}
