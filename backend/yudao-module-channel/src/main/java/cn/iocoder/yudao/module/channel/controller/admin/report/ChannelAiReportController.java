package cn.iocoder.yudao.module.channel.controller.admin.report;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.channel.controller.admin.report.vo.ChannelAiReportRespVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiReportDO;
import cn.iocoder.yudao.module.channel.service.report.ChannelAiReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - AI 工作报告")
@RestController
@RequestMapping("/channel/ai/report")
@Validated
public class ChannelAiReportController {

    @Resource
    private ChannelAiReportService channelAiReportService;

    @GetMapping("/page")
    @Operation(summary = "报告列表")
    @Parameter(name = "periodType", description = "daily、weekly，不填为全部")
    @PreAuthorize("@ss.hasPermission('channel:ai:report')")
    public CommonResult<PageResult<ChannelAiReportRespVO>> page(@Valid PageParam req,
                                                               @RequestParam(value = "periodType", required = false) String periodType) {
        PageResult<ChannelAiReportDO> page = channelAiReportService.page(req, periodType);
        page.getList().forEach(r -> r.setStats(null));
        return success(BeanUtils.toBean(page, ChannelAiReportRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "报告详情（含统计数据）")
    @PreAuthorize("@ss.hasPermission('channel:ai:report')")
    public CommonResult<ChannelAiReportRespVO> get(@RequestParam("id") Long id) {
        ChannelAiReportDO r = channelAiReportService.get(id);
        ChannelAiReportRespVO vo = BeanUtils.toBean(r, ChannelAiReportRespVO.class);
        vo.setStats(r.getStats() == null ? null : JsonUtils.parseObject(r.getStats(), Map.class));
        return success(vo);
    }

    @PostMapping("/generate")
    @Operation(summary = "立即生成（同一周期已有报告时重新生成），返回报告编号")
    @Parameter(name = "periodType", description = "daily 日报（该日）、weekly 周报（该日所在周）", required = true)
    @Parameter(name = "date", description = "日期 yyyy-MM-dd，默认今天", example = "2026-10-20")
    @PreAuthorize("@ss.hasPermission('channel:ai:report')")
    public CommonResult<Long> generate(@RequestParam("periodType") String periodType,
                                       @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return success(channelAiReportService.generate(periodType, date == null ? LocalDate.now() : date,
                ChannelAiReportService.MODE_MANUAL, getLoginUserId()));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除报告")
    @PreAuthorize("@ss.hasPermission('channel:ai:report')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelAiReportService.delete(id);
        return success(true);
    }

}
