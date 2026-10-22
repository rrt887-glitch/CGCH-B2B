package cn.iocoder.yudao.module.channel.controller.admin.meta;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.controller.admin.oauth.OAuthResultPage;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelMetaOAuthService;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - Meta 授权与应用级 Webhook（Facebook 主页、Instagram 私信）")
@RestController
@RequestMapping("/channel/meta")
@Slf4j
public class ChannelMetaController {

    @Resource
    private ChannelMetaOAuthService channelMetaOAuthService;
    @Resource
    private ChannelWhatsAppService whatsAppService;

    @GetMapping("/oauth/authorize-url")
    @Operation(summary = "获取 Facebook 登录授权地址，前端在新窗口打开")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<String> authorizeUrl() {
        return success(channelMetaOAuthService.authorizeUrl(getLoginUserId()));
    }

    @GetMapping(value = "/oauth/callback", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "授权回调（由 Facebook 跳转，无需登录；租户与用户由 state 确定）")
    @PermitAll
    @TenantIgnore
    public String callback(@RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error,
                           @RequestParam(value = "error_description", required = false) String errorDescription) {
        try {
            String result = channelMetaOAuthService.callback(code, state, error == null ? null
                    : (errorDescription == null ? error : errorDescription));
            return OAuthResultPage.html("cgch-meta-oauth", true, "Facebook 授权成功", result + "。可以关闭此页面。");
        } catch (ServiceException e) {
            return OAuthResultPage.html("cgch-meta-oauth", false, "Facebook 授权失败", e.getMessage());
        } catch (Exception e) {
            log.warn("[callback][Facebook 授权异常]", e);
            return OAuthResultPage.html("cgch-meta-oauth", false, "Facebook 授权失败", "服务器处理授权时出错，请稍后重试");
        }
    }

    @PostMapping(value = "/deauthorize")
    @Operation(summary = "取消授权回调：用户在 Facebook 里移除了应用")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> deauthorize(@RequestParam(value = "signed_request", required = false) String signedRequest) {
        return channelMetaOAuthService.deauthorize(signedRequest) ? ResponseEntity.ok("OK")
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body("invalid signed_request");
    }

    @PostMapping(value = "/data-deletion", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "数据删除回调：返回查询地址与确认码（Meta 规定的格式）")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<java.util.Map<String, String>> dataDeletion(@RequestParam(value = "signed_request", required = false) String signedRequest) {
        String code = channelMetaOAuthService.deleteUserData(signedRequest);
        if (code == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(java.util.Map.of("error", "invalid signed_request"));
        }
        return ResponseEntity.ok(java.util.Map.of("url", channelMetaOAuthService.deletionStatusUrl(code), "confirmation_code", code));
    }

    @GetMapping(value = "/data-deletion/status", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "数据删除进度查询页")
    @PermitAll
    @TenantIgnore
    public String dataDeletionStatus(@RequestParam(value = "code", required = false) String code) {
        String n = channelMetaOAuthService.deletionStatus(code);
        return n == null
                ? OAuthResultPage.html("cgch-meta-deletion", false, "Confirmation code not found 未找到该确认码",
                "Please check the code, or contact us by email. 请核对确认码，或通过邮件联系我们。")
                : OAuthResultPage.html("cgch-meta-deletion", true, "Deletion completed 数据已删除",
                "Code " + code + ": " + n + " connected account(s) and their conversations have been deleted. 已删除接入的 " + n + " 个账号及其会话。");
    }

    @GetMapping(value = "/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "应用级 Webhook 回调地址验证")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> verify(@RequestParam(value = "hub.mode", required = false) String mode,
                                         @RequestParam(value = "hub.verify_token", required = false) String token,
                                         @RequestParam(value = "hub.challenge", required = false) String challenge) {
        String result = whatsAppService.verifyAppWebhook(mode, token, challenge);
        return result == null ? ResponseEntity.status(HttpStatus.FORBIDDEN).body("verify token mismatch")
                : ResponseEntity.ok(result);
    }

    @PostMapping("/webhook")
    @Operation(summary = "接收 Facebook 主页与 Instagram 的消息推送")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> receive(@RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
                                          @RequestBody byte[] body) {
        boolean ok = whatsAppService.handleAppWebhook(body, signature);
        return ok ? ResponseEntity.ok("EVENT_RECEIVED") : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
    }

}
