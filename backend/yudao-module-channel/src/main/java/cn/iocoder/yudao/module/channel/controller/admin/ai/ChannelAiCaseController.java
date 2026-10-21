package cn.iocoder.yudao.module.channel.controller.admin.ai;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiCaseDO;
import cn.iocoder.yudao.module.channel.service.cases.ChannelAiCaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 成交案例")
@RestController
@RequestMapping("/channel/ai/case")
@Validated
public class ChannelAiCaseController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private ChannelAiCaseService channelAiCaseService;

    @GetMapping("/candidates")
    @Operation(summary = "已成交的客户（可从中生成案例）")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<List<Map<String, Object>>> candidates() {
        return success(channelAiCaseService.candidates(getLoginUserId()));
    }

    @PostMapping("/generate")
    @Operation(summary = "AI 根据该客户的往来记录生成案例（默认不启用），返回编号")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<Long> generate(@RequestParam("customerId") Long customerId) {
        return success(channelAiCaseService.generate(getLoginUserId(), customerId));
    }

    @GetMapping("/list")
    @Operation(summary = "全部案例")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<List<Map<String, Object>>> list() {
        return success(channelAiCaseService.list().stream().map(ChannelAiCaseController::vo).toList());
    }

    @GetMapping("/get")
    @Operation(summary = "案例详情")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id) {
        return success(vo(channelAiCaseService.get(id)));
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/save")
    @Operation(summary = "新增或修改案例")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<Long> save(@RequestBody Map<String, Object> req) {
        ChannelAiCaseDO k = new ChannelAiCaseDO();
        Object id = req.get("id");
        k.setId(id == null || String.valueOf(id).isBlank() ? null : Long.valueOf(String.valueOf(id)));
        k.setTitle((String) req.get("title"));
        k.setIndustry((String) req.get("industry"));
        k.setProduct((String) req.get("product"));
        k.setCountryCode((String) req.get("countryCode"));
        k.setSummary((String) req.get("summary"));
        k.setEnabled(Boolean.TRUE.equals(req.get("enabled")));
        return success(channelAiCaseService.save(getLoginUserId(), k, (List<String>) req.get("keyPoints"),
                (List<String>) req.get("objections"), (List<String>) req.get("phrases")));
    }

    @PutMapping("/enable")
    @Operation(summary = "开启或关闭：开启后 AI 回复同类询盘时会参考")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<Boolean> enable(@RequestParam("id") Long id, @RequestParam("enabled") Boolean enabled) {
        channelAiCaseService.setEnabled(getLoginUserId(), id, Boolean.TRUE.equals(enabled));
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除案例")
    @PreAuthorize("@ss.hasPermission('channel:ai:case')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelAiCaseService.delete(id);
        return success(true);
    }

    private static Map<String, Object> vo(ChannelAiCaseDO k) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", k.getId());
        m.put("title", k.getTitle());
        m.put("customerId", k.getCustomerId());
        m.put("customerName", k.getCustomerName());
        m.put("industry", k.getIndustry());
        m.put("product", k.getProduct());
        m.put("countryCode", k.getCountryCode());
        m.put("summary", k.getSummary());
        m.put("keyPoints", ChannelAiCaseService.arr(k.getKeyPoints()));
        m.put("objections", ChannelAiCaseService.arr(k.getObjections()));
        m.put("phrases", ChannelAiCaseService.arr(k.getPhrases()));
        m.put("enabled", k.getEnabled());
        m.put("source", k.getSource());
        m.put("credits", k.getCredits());
        m.put("indexed", k.getEmbeddingModel() != null && !k.getEmbeddingModel().isBlank());
        m.put("createTime", k.getCreateTime() == null ? null : k.getCreateTime().format(TIME));
        return m;
    }

}
