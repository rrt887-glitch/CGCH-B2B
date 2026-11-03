package cn.iocoder.yudao.module.channel.controller.admin.mailbatch;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.controller.admin.mailbatch.vo.ChannelMailBatchCreateReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch.ChannelMailBatchDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch.ChannelMailBatchItemDO;
import cn.iocoder.yudao.module.channel.service.mailbatch.ChannelMailBatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 邮件群发")
@RestController
@RequestMapping("/channel/mail-batch")
@Validated
public class ChannelMailBatchController {

    @Resource
    private ChannelMailBatchService channelMailBatchService;

    @PostMapping("/create")
    @Operation(summary = "新建群发（草稿），验证收件人，返回任务编号")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:send')")
    public CommonResult<Long> create(@Valid @RequestBody ChannelMailBatchCreateReqVO req) {
        return success(channelMailBatchService.create(getLoginUserId(), req));
    }

    @PutMapping("/start")
    @Operation(summary = "开始发送")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:send')")
    public CommonResult<Boolean> start(@RequestParam("id") Long id) {
        channelMailBatchService.start(getLoginUserId(), id);
        return success(true);
    }

    @PutMapping("/cancel")
    @Operation(summary = "取消：没发的不再发，已发的保留记录")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:send')")
    public CommonResult<Boolean> cancel(@RequestParam("id") Long id) {
        channelMailBatchService.cancel(getLoginUserId(), id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除任务")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:send')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        channelMailBatchService.delete(getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "我的群发任务，带各状态数量")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:query')")
    public CommonResult<PageResult<Map<String, Object>>> page(@Valid PageParam req) {
        PageResult<ChannelMailBatchDO> p = channelMailBatchService.page(getLoginUserId(), req);
        return success(new PageResult<>(p.getList().stream().map(b -> toMap(b, null)).toList(), p.getTotal()));
    }

    @GetMapping("/get")
    @Operation(summary = "任务详情：各状态数量、回复数和每位收件人")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:query')")
    public CommonResult<Map<String, Object>> get(@RequestParam("id") Long id,
                                                 @RequestParam(value = "status", required = false) String status) {
        ChannelMailBatchDO b = channelMailBatchService.validate(getLoginUserId(), id);
        List<ChannelMailBatchItemDO> items = channelMailBatchService.items(getLoginUserId(), id, status);
        return success(toMap(b, items));
    }

    private Map<String, Object> toMap(ChannelMailBatchDO b, List<ChannelMailBatchItemDO> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("name", b.getName());
        m.put("subject", b.getSubject());
        m.put("contentType", b.getContentType());
        m.put("ratePerMinute", b.getRatePerMinute());
        m.put("accountIds", Arrays.stream(String.valueOf(b.getAccountIds()).split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList());
        m.put("status", b.getStatus());
        m.put("total", b.getTotal());
        m.put("counts", channelMailBatchService.counts(b.getId()));
        m.put("createTime", b.getCreateTime());
        m.put("startTime", b.getStartTime());
        m.put("finishTime", b.getFinishTime());
        if (items != null) {
            m.put("content", b.getContent());
            Set<Long> replied = channelMailBatchService.repliedItemIds(items);
            m.put("replied", replied.size());
            m.put("items", items.stream().map(i -> {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", i.getId());
                x.put("customerId", i.getCustomerId());
                x.put("name", i.getName());
                x.put("company", i.getCompany());
                x.put("email", i.getEmail());
                x.put("accountId", i.getAccountId());
                x.put("status", i.getStatus());
                x.put("reason", i.getReason());
                x.put("sentTime", i.getSentTime());
                x.put("sentRefId", i.getSentRefId());
                x.put("replied", replied.contains(i.getId()));
                x.put("unsubscribed", Boolean.TRUE.equals(i.getUnsubscribed()));
                return x;
            }).toList());
        }
        return m;
    }

    // ==================== 公开：退订 ====================

    @GetMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "退订页（收件人点邮件里的链接）")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> unsubscribePage(@RequestParam(value = "t", required = false) String token) {
        String email = channelMailBatchService.unsubscribe(token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page(email));
    }

    @PostMapping(value = "/unsubscribe", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(summary = "一键退订（邮箱客户端按 List-Unsubscribe-Post 调用）")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> unsubscribeOneClick(@RequestParam(value = "t", required = false) String token) {
        String email = channelMailBatchService.unsubscribe(token);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(page(email));
    }

    private static String page(String email) {
        String msg = email == null
                ? "<h1>Link expired</h1><p>This unsubscribe link is not valid. Please reply to the email and ask us to remove you.</p>"
                : "<h1>You have been unsubscribed</h1><p><b>" + ChannelMailBatchServiceEscape.escape(email)
                + "</b> will no longer receive these emails.</p>";
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Unsubscribe</title><style>body{margin:0;font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#f6f7f9;color:#111827}"
                + "main{max-width:480px;margin:80px auto;padding:32px;background:#fff;border:1px solid #e5e7eb;border-radius:12px}"
                + "h1{font-size:20px;margin:0 0 12px}p{font-size:15px;line-height:1.6;color:#4b5563;margin:0}</style></head>"
                + "<body><main>" + msg + "</main></body></html>";
    }

    /** 页面上的邮箱转义 */
    private static final class ChannelMailBatchServiceEscape {
        static String escape(String s) {
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }
    }

}
