package cn.iocoder.yudao.module.channel.controller.admin.reactivate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.controller.admin.reactivate.vo.ChannelReactivateTaskCreateReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateItemDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateTaskDO;
import cn.iocoder.yudao.module.channel.service.reactivate.ChannelReactivateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 客户唤醒")
@RestController
@RequestMapping("/channel/reactivate")
@Validated
public class ChannelReactivateController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private ChannelReactivateService channelReactivateService;

    @GetMapping("/candidates")
    @Operation(summary = "久未联系的客户（在本人数据权限范围内，最多 500 位）")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<List<Map<String, Object>>> candidates(@RequestParam(value = "inactiveDays", defaultValue = "90") Integer inactiveDays,
                                                             @RequestParam(value = "ownerUserId", required = false) Long ownerUserId,
                                                             @RequestParam(value = "keyword", required = false) String keyword) {
        return success(channelReactivateService.candidates(getLoginUserId(), inactiveDays, ownerUserId, keyword));
    }

    @PostMapping("/task/create")
    @Operation(summary = "创建唤醒任务，AI 在后台逐个生成内容，返回任务编号")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Long> create(@RequestBody ChannelReactivateTaskCreateReqVO req) {
        return success(channelReactivateService.createTask(getLoginUserId(), req));
    }

    @GetMapping("/task/page")
    @Operation(summary = "唤醒任务列表")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<PageResult<Map<String, Object>>> page(@Valid PageParam req) {
        PageResult<ChannelReactivateTaskDO> p = channelReactivateService.taskPage(req);
        return success(new PageResult<>(p.getList().stream().map(t -> task(t, false)).toList(), p.getTotal()));
    }

    @GetMapping("/task/get")
    @Operation(summary = "唤醒任务详情：各状态数量、回复数和每位客户的内容")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(task(channelReactivateService.getTask(id), true));
    }

    @DeleteMapping("/task/delete")
    @Operation(summary = "删除唤醒任务")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelReactivateService.deleteTask(id);
        return success(true);
    }

    @PostMapping("/task/send")
    @Operation(summary = "发送任务中所有已确认的内容（后台逐条发出），返回条数")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Integer> send(@RequestParam("id") Long id) {
        return success(channelReactivateService.send(id));
    }

    @PutMapping("/item/update")
    @Operation(summary = "修改邮件主题和正文")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Boolean> update(@RequestBody Map<String, Object> req) {
        channelReactivateService.updateItem(Long.valueOf(String.valueOf(req.get("id"))),
                (String) req.get("subject"), (String) req.get("content"));
        return success(true);
    }

    @PostMapping("/item/status")
    @Operation(summary = "批量改状态：approve 确认、skip 跳过、draft 撤回确认，返回生效条数")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Integer> status(@RequestParam("action") String action, @RequestBody List<Long> ids) {
        return success(channelReactivateService.changeStatus(ids, action));
    }

    @PostMapping("/item/regenerate")
    @Operation(summary = "让 AI 重新写这一封")
    @PreAuthorize("@ss.hasPermission('channel:reactivate')")
    public CommonResult<Boolean> regenerate(@RequestParam("id") Long id) {
        channelReactivateService.regenerate(id);
        return success(true);
    }

    private Map<String, Object> task(ChannelReactivateTaskDO t, boolean withItems) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("inactiveDays", t.getInactiveDays());
        m.put("channel", t.getChannel());
        m.put("instructions", t.getInstructions());
        m.put("waTemplateName", t.getWaTemplateName());
        m.put("waTemplateLanguage", t.getWaTemplateLanguage());
        m.put("status", t.getStatus());
        m.put("userId", t.getUserId());
        m.put("createTime", t.getCreateTime() == null ? null : t.getCreateTime().format(TIME));
        List<ChannelReactivateItemDO> items = channelReactivateService.items(t.getId());
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : List.of("generating", "draft", "approved", "sent", "failed", "skipped")) {
            counts.put(s, 0L);
        }
        items.forEach(i -> counts.merge(i.getStatus(), 1L, Long::sum));
        m.put("total", items.size());
        m.put("counts", counts);
        long replied = 0;
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChannelReactivateItemDO i : items) {
            boolean r = channelReactivateService.replied(i);
            if (r) {
                replied++;
            }
            if (withItems) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", i.getId());
                x.put("customerId", i.getCustomerId());
                x.put("customerName", i.getCustomerName());
                x.put("ownerUserId", i.getOwnerUserId());
                x.put("lastContactTime", i.getLastContactTime() == null ? null : i.getLastContactTime().format(TIME));
                x.put("channel", i.getChannel());
                x.put("recipient", i.getRecipient());
                x.put("subject", i.getSubject());
                x.put("content", i.getContent());
                x.put("status", i.getStatus());
                x.put("reasonDetail", i.getReasonDetail());
                x.put("credits", i.getCredits());
                x.put("sentTime", i.getSentTime() == null ? null : i.getSentTime().format(TIME));
                x.put("sentRefId", i.getSentRefId());
                x.put("replied", r);
                list.add(x);
            }
        }
        m.put("replied", replied);
        if (withItems) {
            m.put("items", list);
        }
        return m;
    }

}
