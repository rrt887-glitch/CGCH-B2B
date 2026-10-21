package cn.iocoder.yudao.module.channel.controller.admin.worktime;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.channel.controller.admin.worktime.vo.ChannelWorkTimeVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelAiDeferredDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelWorkTimeDO;
import cn.iocoder.yudao.module.channel.dal.mysql.worktime.ChannelAiDeferredMapper;
import cn.iocoder.yudao.module.channel.service.worktime.ChannelWorkTimeService;
import cn.iocoder.yudao.module.channel.service.worktime.ChannelWorkTimeService.Day;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 工作时间")
@RestController
@RequestMapping("/channel/work-time")
@Validated
public class ChannelWorkTimeController {

    @Resource
    private ChannelWorkTimeService channelWorkTimeService;
    @Resource
    private ChannelAiDeferredMapper channelAiDeferredMapper;

    @GetMapping("/get")
    @Operation(summary = "获得工作时间设置（未设置时返回默认值）")
    @PreAuthorize("@ss.hasPermission('channel:work-time:config')")
    public CommonResult<ChannelWorkTimeVO> get() {
        ChannelWorkTimeDO c = channelWorkTimeService.getConfig();
        ChannelWorkTimeVO vo = new ChannelWorkTimeVO();
        vo.setEnabled(c.getEnabled());
        vo.setTimezone(c.getTimezone());
        vo.setSchedule(JsonUtils.parseArray(c.getSchedule(), Day.class));
        vo.setHolidays(JsonUtils.parseArray(c.getHolidays(), String.class));
        vo.setExtraWorkdays(JsonUtils.parseArray(c.getExtraWorkdays(), String.class));
        vo.setInHoursPolicy(c.getInHoursPolicy());
        vo.setTakeoverMinutes(c.getTakeoverMinutes());
        vo.setOffHoursPolicy(c.getOffHoursPolicy());
        return success(vo);
    }

    @PutMapping("/save")
    @Operation(summary = "保存工作时间设置")
    @PreAuthorize("@ss.hasPermission('channel:work-time:config')")
    public CommonResult<Boolean> save(@RequestBody ChannelWorkTimeVO req) {
        channelWorkTimeService.save(ChannelWorkTimeDO.builder().enabled(req.getEnabled()).timezone(req.getTimezone())
                .schedule(JsonUtils.toJsonString(req.getSchedule())).holidays(JsonUtils.toJsonString(req.getHolidays()))
                .extraWorkdays(JsonUtils.toJsonString(req.getExtraWorkdays())).inHoursPolicy(req.getInHoursPolicy())
                .takeoverMinutes(req.getTakeoverMinutes()).offHoursPolicy(req.getOffHoursPolicy()).build());
        return success(true);
    }

    @GetMapping("/status")
    @Operation(summary = "当前是否工作时间、下次切换时间、等待业务员回复的消息数")
    @PreAuthorize("@ss.hasPermission('channel:work-time:config')")
    public CommonResult<Map<String, Object>> status() {
        Map<String, Object> m = channelWorkTimeService.status();
        m.put("waitingCount", channelAiDeferredMapper.selectCount(ChannelAiDeferredDO::getStatus, ChannelAiDeferredDO.STATUS_WAITING));
        return success(m);
    }

    @GetMapping("/deferred/latest")
    @Operation(summary = "某个对象最近一次的等待项（调试与测试用）")
    @PreAuthorize("@ss.hasPermission('channel:work-time:config')")
    public CommonResult<ChannelAiDeferredDO> latestDeferred(@RequestParam("channel") String channel, @RequestParam("refId") Long refId) {
        return success(channelAiDeferredMapper.selectLatest(channel, refId));
    }

}
