package cn.iocoder.yudao.module.channel.controller.admin.evolution;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.controller.admin.evolution.vo.ChannelEvolutionProxyRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.evolution.vo.ChannelEvolutionProxySaveReqVO;
import cn.iocoder.yudao.module.channel.service.evolution.ChannelEvolutionProxyService;
import cn.iocoder.yudao.module.channel.service.evolution.ChannelEvolutionService;
import cn.iocoder.yudao.module.channel.service.evolution.EvolutionClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * WhatsApp 扫码接入（Evolution API）：开始连接（二维码或配对码）、轮询进度、放弃连接；接收 Evolution 推送（无需登录，校验令牌）。
 */
@Tag(name = "管理后台 - WhatsApp 扫码接入")
@RestController
@RequestMapping("/channel/evolution")
@Validated
public class ChannelEvolutionController {

    @Resource
    private ChannelEvolutionService channelEvolutionService;
    @Resource
    private ChannelEvolutionProxyService channelEvolutionProxyService;

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

    // ==================== 出口代理（平台运营在「客户管理」里按客户配置） ====================

    @GetMapping("/proxy")
    @Operation(summary = "查看某家客户的出口代理（平台）")
    @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<ChannelEvolutionProxyRespVO> getProxy(@RequestParam("tenantId") Long tenantId) {
        return success(channelEvolutionProxyService.getProxy(tenantId));
    }

    @PutMapping("/proxy")
    @Operation(summary = "保存某家客户的出口代理（平台）：可同时把代理应用到这家客户已有的号码")
    @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<List<String>> saveProxy(@Valid @RequestBody ChannelEvolutionProxySaveReqVO reqVO) {
        return success(channelEvolutionProxyService.saveProxy(reqVO));
    }

    @PostMapping("/proxy/apply")
    @Operation(summary = "把已保存的代理重新应用到某家客户的全部号码（平台）")
    @PreAuthorize("@ss.hasPermission('system:tenant:update')")
    public CommonResult<List<String>> applyProxy(@RequestParam("tenantId") Long tenantId) {
        return success(channelEvolutionProxyService.applyToTenant(tenantId));
    }

    @GetMapping("/proxy/mine")
    @Operation(summary = "本公司是否配置了出口代理（客户管理员可见，只返回是否启用和出口主机）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:query')")
    public CommonResult<Map<String, Object>> myProxy() {
        return success(channelEvolutionProxyService.myProxySummary());
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
