package cn.iocoder.yudao.module.channel.controller.admin.policy;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSendPolicyDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSuppressionDO;
import cn.iocoder.yudao.module.channel.service.policy.ChannelSendPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 发送策略")
@RestController
@RequestMapping("/channel")
@Validated
public class ChannelSendPolicyController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private ChannelSendPolicyService channelSendPolicyService;

    @GetMapping("/send-policy/get")
    @Operation(summary = "获得发送策略（未设置时返回默认值）")
    @PreAuthorize("@ss.hasPermission('channel:send-policy')")
    public CommonResult<ChannelSendPolicyDO> get() {
        return success(channelSendPolicyService.getPolicy());
    }

    @PutMapping("/send-policy/save")
    @Operation(summary = "保存发送策略")
    @PreAuthorize("@ss.hasPermission('channel:send-policy')")
    public CommonResult<Boolean> save(@RequestBody ChannelSendPolicyDO req) {
        channelSendPolicyService.savePolicy(req);
        return success(true);
    }

    @GetMapping("/send-policy/usage")
    @Operation(summary = "各邮箱 / WhatsApp 号码最近 1 小时和 24 小时的发送量")
    @PreAuthorize("@ss.hasPermission('channel:send-policy')")
    public CommonResult<List<Map<String, Object>>> usage() {
        return success(channelSendPolicyService.usage());
    }

    @GetMapping("/suppression/page")
    @Operation(summary = "退订与黑名单")
    @PreAuthorize("@ss.hasPermission('channel:send-policy')")
    public CommonResult<PageResult<Map<String, Object>>> page(@Valid PageParam req,
                                                             @RequestParam(value = "channel", required = false) String channel,
                                                             @RequestParam(value = "reason", required = false) String reason,
                                                             @RequestParam(value = "keyword", required = false) String keyword) {
        PageResult<ChannelSuppressionDO> p = channelSendPolicyService.page(req, channel, reason, keyword);
        return success(new PageResult<>(p.getList().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("channel", s.getChannel());
            m.put("value", s.getAddress());
            m.put("reason", s.getReason());
            m.put("remark", s.getRemark());
            m.put("userId", s.getUserId());
            m.put("createTime", s.getCreateTime() == null ? null : s.getCreateTime().format(TIME));
            return m;
        }).toList(), p.getTotal()));
    }

    @PostMapping("/suppression/create")
    @Operation(summary = "手动加入黑名单：邮箱、@域名或 WhatsApp 号码")
    @PreAuthorize("@ss.hasPermission('channel:send-policy')")
    public CommonResult<Long> create(@RequestBody Map<String, String> req) {
        return success(channelSendPolicyService.add(req.get("channel"), req.get("value"), ChannelSuppressionDO.REASON_MANUAL,
                req.get("remark"), getLoginUserId()).getId());
    }

    @DeleteMapping("/suppression/delete")
    @Operation(summary = "移出名单")
    @PreAuthorize("@ss.hasPermission('channel:send-policy')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelSendPolicyService.remove(id);
        return success(true);
    }

}
