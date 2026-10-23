package cn.iocoder.yudao.module.channel.controller.admin.notify;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.notify.ChannelNotifyRuleDO;
import cn.iocoder.yudao.module.channel.dal.mysql.notify.ChannelNotifyRuleMapper;
import cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

@Tag(name = "管理后台 - 通知规则（当前登录账号）")
@RestController
@RequestMapping("/channel/notify-rule")
@Validated
public class ChannelNotifyRuleController {

    @Resource
    private ChannelNotifyRuleMapper channelNotifyRuleMapper;

    @GetMapping("/list")
    @Operation(summary = "我的通知规则，以及可选的消息类型")
    public CommonResult<Map<String, Object>> list() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("rules", channelNotifyRuleMapper.selectByUser(getLoginUserId()));
        List<Map<String, String>> types = new ArrayList<>();
        ChannelNotifyService.TYPES.forEach((k, v) -> types.add(Map.of("value", k, "label", v[0], "sample", v[1])));
        r.put("types", types);
        return success(r);
    }

    @PostMapping("/save")
    @Operation(summary = "新增或修改规则")
    public CommonResult<Long> save(@RequestBody ChannelNotifyRuleDO req) {
        Long userId = getLoginUserId();
        if (!ChannelNotifyService.TYPES.containsKey(req.getType())) {
            throw exception(AI_LLM_FAIL, "请选择消息类型");
        }
        String channel = ChannelNotifyRuleDO.CHANNEL_EMAIL.equals(req.getChannel()) ? ChannelNotifyRuleDO.CHANNEL_EMAIL : ChannelNotifyRuleDO.CHANNEL_SITE;
        boolean dup = channelNotifyRuleMapper.selectByUser(userId).stream()
                .anyMatch(x -> !Objects.equals(x.getId(), req.getId()) && x.getType().equals(req.getType()) && x.getChannel().equals(channel));
        if (dup) {
            throw exception(AI_LLM_FAIL, "已有相同的规则");
        }
        ChannelNotifyRuleDO r = ChannelNotifyRuleDO.builder().userId(userId).type(req.getType()).channel(channel)
                .enabled(req.getEnabled() == null || req.getEnabled()).build();
        if (req.getId() == null) {
            channelNotifyRuleMapper.insert(r);
        } else {
            ChannelNotifyRuleDO old = channelNotifyRuleMapper.selectById(req.getId());
            if (old == null || !Objects.equals(old.getUserId(), userId)) {
                throw exception(AI_LLM_FAIL, "规则不存在");
            }
            r.setId(req.getId());
            channelNotifyRuleMapper.updateById(r);
        }
        return success(r.getId());
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除规则")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        ChannelNotifyRuleDO old = channelNotifyRuleMapper.selectById(id);
        if (old != null && Objects.equals(old.getUserId(), getLoginUserId())) {
            channelNotifyRuleMapper.deleteById(id);
        }
        return success(true);
    }

}
