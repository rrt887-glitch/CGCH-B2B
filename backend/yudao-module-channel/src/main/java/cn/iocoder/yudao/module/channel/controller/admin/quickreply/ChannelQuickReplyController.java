package cn.iocoder.yudao.module.channel.controller.admin.quickreply;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.quickreply.ChannelQuickReplyDO;
import cn.iocoder.yudao.module.channel.service.quickreply.ChannelQuickReplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 快捷回复")
@RestController
@RequestMapping("/channel/quick-reply")
@Validated
public class ChannelQuickReplyController {

    @Resource
    private ChannelQuickReplyService channelQuickReplyService;

    @GetMapping("/list")
    @Operation(summary = "我能用的快捷回复（全员 + 个人）")
    @PreAuthorize("@ss.hasAnyPermissions('channel:wa-message:send', 'channel:mail-message:send')")
    public CommonResult<List<ChannelQuickReplyDO>> list(@RequestParam(value = "keyword", required = false) String keyword) {
        return success(channelQuickReplyService.list(getLoginUserId(), keyword));
    }

    @PostMapping("/save")
    @Operation(summary = "新建或修改；全员快捷回复需要维护权限")
    @PreAuthorize("@ss.hasAnyPermissions('channel:wa-message:send', 'channel:mail-message:send')")
    public CommonResult<Long> save(@RequestBody ChannelQuickReplyDO req) {
        return success(channelQuickReplyService.save(getLoginUserId(), req));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除")
    @PreAuthorize("@ss.hasAnyPermissions('channel:wa-message:send', 'channel:mail-message:send')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelQuickReplyService.delete(getLoginUserId(), id);
        return success(true);
    }

    @PostMapping("/used")
    @Operation(summary = "记录一次使用")
    @PreAuthorize("@ss.hasAnyPermissions('channel:wa-message:send', 'channel:mail-message:send')")
    public CommonResult<Boolean> used(@RequestParam("id") Long id) {
        channelQuickReplyService.used(getLoginUserId(), id);
        return success(true);
    }

}
