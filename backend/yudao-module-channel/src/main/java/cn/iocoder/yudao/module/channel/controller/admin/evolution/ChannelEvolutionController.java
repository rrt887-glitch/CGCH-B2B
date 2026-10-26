package cn.iocoder.yudao.module.channel.controller.admin.evolution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.service.evolution.ChannelEvolutionService;
import cn.iocoder.yudao.module.channel.service.evolution.EvolutionClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * WhatsApp 扫码接入（Evolution API）：开始连接（二维码或配对码）、轮询进度、放弃连接；接收 Evolution 推送（无需登录，校验令牌）。
 */
@Tag(name = "管理后台 - WhatsApp 扫码接入")
@RestController
@RequestMapping("/channel/evolution")
public class ChannelEvolutionController {

    @Resource
    private ChannelEvolutionService channelEvolutionService;

    @GetMapping("/config")
    @Operation(summary = "是否已开通扫码接入")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:query')")
    public CommonResult<Map<String, Object>> config() {
        return success(Map.of("enabled", channelEvolutionService.enabled()));
    }

    @PostMapping("/session")
    @Operation(summary = "开始连接：number 为空返回二维码，填手机号（含国家码）返回配对码；accountId 为重新扫码的账号")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<ChannelEvolutionService.Session> start(@RequestBody Map<String, Object> req) {
        Object number = req.get("number");
        Object accountId = req.get("accountId");
        return success(channelEvolutionService.start(getLoginUserId(), number == null ? null : number.toString(),
                accountId == null || accountId.toString().isBlank() ? null : Long.valueOf(accountId.toString())));
    }

    @GetMapping("/session")
    @Operation(summary = "连接进度：connecting（附最新二维码 / 配对码）、open、expired")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<ChannelEvolutionService.Session> status(@RequestParam("session") String session) {
        return success(channelEvolutionService.status(getLoginUserId(), session));
    }

    @DeleteMapping("/session")
    @Operation(summary = "放弃连接")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Boolean> cancel(@RequestParam("session") String session) {
        channelEvolutionService.cancel(getLoginUserId(), session);
        return success(true);
    }

    @PostMapping("/webhook")
    @Operation(summary = "接收 Evolution API 推送")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> webhook(@RequestHeader(value = EvolutionClient.TOKEN_HEADER, required = false) String token,
                                          @RequestBody byte[] body) {
        return channelEvolutionService.handleWebhook(body, token) ? ResponseEntity.ok("ok")
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid token");
    }

}
