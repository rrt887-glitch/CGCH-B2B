package cn.iocoder.yudao.module.channel.controller.admin.unipile;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.service.unipile.ChannelUnipileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import java.util.Map;

/**
 * Unipile 接入：托管扫码、回调、推送、断开。
 *
 * 隔离：每个公司一个 Scope + 专属 Key，读写一律用本公司 Key。
 */
@Tag(name = "管理后台 - Unipile 接入")
@RestController
@RequestMapping("/channel/unipile")
@Slf4j
public class ChannelUnipileController {

    /** Unipile 的签名头 */
    private static final String SIGNATURE_HEADER = "Unipile-Signature";

    @Resource
    private ChannelUnipileService channelUnipileService;

    @GetMapping("/credential")
    @Operation(summary = "本公司的 Unipile 凭证（脱敏）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:query')")
    public CommonResult<Map<String, Object>> credential() {
        return CommonResult.success(channelUnipileService.credentialInfo());
    }

    @PostMapping("/connect")
    @Operation(summary = "生成托管授权链接（provider：whatsapp / linkedin / instagram / facebook / telegram）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Map<String, Object>> connect(
            @RequestParam(value = "provider", required = false, defaultValue = "whatsapp") String provider,
            @RequestParam(value = "force", required = false, defaultValue = "false") Boolean force) {
        return CommonResult.success(channelUnipileService.connect(provider, Boolean.TRUE.equals(force)));
    }

    @PostMapping("/recover")
    @Operation(summary = "找回 Unipile 上已存在、但后台没记录的账号")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Integer> recover() {
        return CommonResult.success(channelUnipileService.recoverAccounts(getLoginUserId()));
    }
    @DeleteMapping("/account")
    @Operation(summary = "断开并删除号码")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:delete')")
    public CommonResult<Boolean> disconnect(@RequestParam("id") Long id) {
        channelUnipileService.disconnect(id);
        return CommonResult.success(true);
    }

    /**
     * 扫码完成后的回调（Unipile 跳转过来，带 account_id / provider / state）。
     * 这里不校验登录态：Unipile 无法携带我们的令牌；安全靠「用该租户 Key 校验账号归属」。
     */
    @GetMapping("/callback")
    @Operation(summary = "托管扫码回调")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> callback(@RequestParam(value = "account_id", required = false) String accountId,
                                           @RequestParam(value = "state", required = false) String state,
                                           @RequestParam(value = "error_title", required = false) String errorTitle) {
        if (errorTitle != null) {
            return ResponseEntity.ok("授权失败：" + errorTitle + "，请回到后台重试");
        }
        // state 是一次性令牌：换回「哪个租户、哪个成员、哪个渠道」
        cn.iocoder.yudao.module.channel.dal.dataobject.unipile.ChannelUnipileAuthStateDO authState =
                channelUnipileService.consumeAuthState(state);
        if (authState == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("授权已过期或已被使用，请回到后台重新点「接入」");
        }
        try {
            channelUnipileService.handleCallback(authState.getTenantId(), authState.getUserId(),
                    authState.getProvider(), accountId);
            return ResponseEntity.ok("连接成功，可以回到后台查看");
        } catch (Exception e) {
            log.warn("[callback][绑定失败] tenant={} user={} provider={} account={} err={}",
                    authState.getTenantId(), authState.getUserId(), authState.getProvider(), accountId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("绑定失败：" + e.getMessage());
        }
    }

    @PostMapping("/webhook")
    @Operation(summary = "接收 Unipile 推送")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> webhook(@RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
                                          @RequestHeader(value = "X-Unipile-Signature", required = false) String signature2,
                                          @RequestBody byte[] body) {
        String sig = signature != null ? signature : signature2;
        return channelUnipileService.handleWebhook(body, sig) ? ResponseEntity.ok("ok")
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
    }
}
