package cn.iocoder.yudao.module.channel.controller.admin.research;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.research.ChannelResearchDO;
import cn.iocoder.yudao.module.channel.service.research.ChannelResearchService;
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

@Tag(name = "管理后台 - 深度背调")
@RestController
@RequestMapping("/channel/research")
@Validated
public class ChannelResearchController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private ChannelResearchService channelResearchService;

    @PostMapping("/create")
    @Operation(summary = "发起背调（官网、客户、线索至少一个），后台执行，返回编号")
    @PreAuthorize("@ss.hasPermission('channel:research')")
    public CommonResult<Long> create(@RequestBody Map<String, Object> req) {
        return success(channelResearchService.create(getLoginUserId(), (String) req.get("website"), (String) req.get("companyName"),
                toLong(req.get("customerId")), toLong(req.get("clueId"))));
    }

    @GetMapping("/page")
    @Operation(summary = "背调记录")
    @PreAuthorize("@ss.hasPermission('channel:research')")
    public CommonResult<PageResult<Map<String, Object>>> page(@Valid PageParam req,
                                                             @RequestParam(value = "customerId", required = false) Long customerId,
                                                             @RequestParam(value = "clueId", required = false) Long clueId) {
        PageResult<ChannelResearchDO> p = channelResearchService.page(req, customerId, clueId);
        return success(new PageResult<>(p.getList().stream().map(r -> vo(r, false)).toList(), p.getTotal()));
    }

    @GetMapping("/latest-by-customers")
    @Operation(summary = "每个客户最近一次背调的状态与概况")
    @PreAuthorize("@ss.hasPermission('channel:research')")
    public CommonResult<List<Map<String, Object>>> latestByCustomers(@RequestParam("customerIds") List<Long> customerIds) {
        return success(channelResearchService.latestByCustomers(customerIds.stream().limit(200).toList()).stream().map(r -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("customerId", r.getCustomerId());
            m.put("status", r.getStatus());
            m.put("createTime", r.getCreateTime());
            Object report = vo(r, true).get("report");
            m.put("summary", report instanceof Map<?, ?> rep ? rep.get("summary") : null);
            m.put("confidence", report instanceof Map<?, ?> rep ? rep.get("confidence") : null);
            return m;
        }).toList());
    }

    @GetMapping("/get")
    @Operation(summary = "背调报告")
    @PreAuthorize("@ss.hasPermission('channel:research')")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(vo(channelResearchService.get(id), true));
    }

    @PostMapping("/apply")
    @Operation(summary = "把公司名、官网、国家补到关联客户 / 线索的空字段，并把概况追加到备注；返回更新的字段")
    @PreAuthorize("@ss.hasPermission('channel:research')")
    public CommonResult<List<String>> apply(@RequestParam("id") Long id) {
        return success(channelResearchService.apply(id));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除背调记录")
    @PreAuthorize("@ss.hasPermission('channel:research')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelResearchService.delete(id);
        return success(true);
    }

    private static Long toLong(Object o) {
        return o == null || String.valueOf(o).isBlank() ? null : Long.valueOf(String.valueOf(o));
    }

    private static Map<String, Object> vo(ChannelResearchDO r, boolean full) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("companyName", r.getCompanyName());
        m.put("website", r.getWebsite());
        m.put("customerId", r.getCustomerId());
        m.put("clueId", r.getClueId());
        m.put("status", r.getStatus());
        m.put("reasonDetail", r.getReasonDetail());
        m.put("credits", r.getCredits());
        m.put("userId", r.getUserId());
        m.put("createTime", r.getCreateTime() == null ? null : r.getCreateTime().format(TIME));
        if (full) {
            m.put("report", r.getReport() == null ? null : JsonUtils.parseObject(r.getReport(), Map.class));
            m.put("sources", r.getSources() == null ? List.of() : JsonUtils.parseArray(r.getSources(), Map.class));
        }
        return m;
    }

}
