package cn.iocoder.yudao.module.channel.controller.admin.whatsapp;

import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Meta 回调入口。在 Meta 开发者后台的 WhatsApp → 配置 中填写：
 * 回调地址 https://你的域名/admin-api/channel/whatsapp/webhook/{号码编号}，验证令牌取号码详情里的值，
 * 并订阅 messages 字段。
 */
@Tag(name = "WhatsApp Webhook（Meta 回调，无需登录）")
@RestController
@RequestMapping("/channel/whatsapp/webhook")
public class ChannelWaWebhookController {

    @Resource
    private ChannelWhatsAppService whatsAppService;

    @GetMapping(value = "/{accountId}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "回调地址验证")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> verify(@PathVariable("accountId") Long accountId,
                                         @RequestParam(value = "hub.mode", required = false) String mode,
                                         @RequestParam(value = "hub.verify_token", required = false) String token,
                                         @RequestParam(value = "hub.challenge", required = false) String challenge) {
        String result = whatsAppService.verifyWebhook(accountId, mode, token, challenge);
        return result == null ? ResponseEntity.status(HttpStatus.FORBIDDEN).body("verify token mismatch")
                : ResponseEntity.ok(result);
    }

    @PostMapping("/{accountId}")
    @Operation(summary = "接收消息与状态推送")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> receive(@PathVariable("accountId") Long accountId,
                                          @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                          @RequestBody byte[] body) {
        boolean ok = whatsAppService.handleWebhook(accountId, body, signature);
        return ok ? ResponseEntity.ok("EVENT_RECEIVED") : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
    }

}
