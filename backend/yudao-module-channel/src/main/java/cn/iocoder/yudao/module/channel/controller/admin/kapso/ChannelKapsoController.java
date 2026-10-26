package cn.iocoder.yudao.module.channel.controller.admin.kapso;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.service.kapso.ChannelKapsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * WhatsApp 经 Kapso 接入：创建绑定链接、查询绑定结果、绑定页跳回；接收 Kapso 推送（无需登录，校验签名）。
 * 推送地址 https://你的域名/admin-api/channel/kapso/webhook。
 */
@Tag(name = "管理后台 - WhatsApp（Kapso）")
@RestController
@RequestMapping("/channel/kapso")
public class ChannelKapsoController {

    @Resource
    private ChannelKapsoService channelKapsoService;

    @PostMapping("/onboarding-link")
    @Operation(summary = "创建绑定链接（mode=coexist 共存模式，new 纯 API 号码）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<ChannelKapsoService.Link> link(@RequestBody Map<String, String> req) {
        return success(channelKapsoService.createLink(getLoginUserId(), !"new".equals(req.get("mode"))));
    }

    @GetMapping("/onboarding-result")
    @Operation(summary = "绑定结果：完成后返回账号编号与号码")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Map<String, Object>> result(@RequestParam("ref") String ref) {
        ChannelWaAccountDO a = channelKapsoService.result(getLoginUserId(), ref);
        if (a == null) {
            return success(null);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", a.getId());
        r.put("displayPhone", a.getDisplayPhone());
        return success(r);
    }

    @GetMapping("/return")
    @Operation(summary = "Kapso 绑定页完成后跳回（在授权弹窗里打开）")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> back(@RequestParam(value = "ref", required = false) String ref,
                                       @RequestParam(value = "phone_number_id", required = false) String phoneNumberId,
                                       @RequestParam(value = "failed", required = false) String failed,
                                       @RequestParam(value = "error_code", required = false) String errorCode) {
        boolean ok = channelKapsoService.handleReturn(ref, phoneNumberId, failed != null || errorCode != null);
        String title = ok ? "WhatsApp 绑定成功" : "WhatsApp 绑定未完成";
        String tip = ok ? "可以关闭这个窗口，回到系统继续操作。" : "请关闭这个窗口，回到系统重新点「开始连接」。";
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + title + "</title><style>body{font-family:system-ui,sans-serif;background:#f8fafc;color:#111827;display:flex;"
                + "align-items:center;justify-content:center;min-height:100vh;margin:0}div{text-align:center;padding:24px}h1{font-size:20px;color:"
                + (ok ? "#16a34a" : "#dc2626") + "}p{color:#6b7280}</style></head><body><div><h1>" + title + "</h1><p>" + tip + "</p></div>"
                + "<script>try{window.opener&&window.opener.postMessage({type:'cgch-kapso',ok:" + ok + "},'*')}catch(e){}"
                + (ok ? "setTimeout(function(){window.close()},1500)" : "") + "</script></body></html>";
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8)).body(html);
    }

    @PostMapping("/webhook")
    @Operation(summary = "接收 Kapso 推送")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> webhook(@RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
                                          @RequestHeader(value = "X-Webhook-Event", required = false) String event,
                                          @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
                                          @RequestBody byte[] body) {
        return channelKapsoService.handleWebhook(body, signature, event, idempotencyKey) ? ResponseEntity.ok("ok")
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
    }

}
