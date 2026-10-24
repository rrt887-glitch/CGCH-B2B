package cn.iocoder.yudao.module.channel.controller.admin.webim;

import cn.hutool.core.io.IoUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.service.webim.ChannelWebImService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.Data;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * 官网 IM。后台：绑定网站（域名 + 官网名称）；访客端：嵌入脚本、发消息、轮询新消息（无需登录，凭站点密钥）。
 * 嵌入代码：&lt;script src="https://你的域名/admin-api/channel/web-im/widget.js?k=站点密钥" async&gt;&lt;/script&gt;
 */
@Tag(name = "官网 IM")
@RestController
@RequestMapping("/channel/web-im")
public class ChannelWebImController {

    @Resource
    private ChannelWebImService channelWebImService;

    private static volatile String widgetJs;

    @Data
    public static class SiteReq {
        private Long id;
        private String domain;
        private String name;
    }

    @Data
    public static class SendReq {
        private String k;
        private String visitorId;
        private String text;
        private String page;
    }

    // ==================== 后台 ====================

    @PostMapping("/site")
    @Operation(summary = "绑定网站")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Long> createSite(@RequestBody SiteReq req) {
        return success(channelWebImService.createSite(getLoginUserId(), req.getDomain(), req.getName()));
    }

    @PutMapping("/site")
    @Operation(summary = "修改绑定的网站")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:update')")
    public CommonResult<Boolean> updateSite(@RequestBody SiteReq req) {
        channelWebImService.updateSite(getLoginUserId(), req.getId(), req.getDomain(), req.getName());
        return success(true);
    }

    // ==================== 访客端（无需登录） ====================

    @GetMapping(value = "/widget.js", produces = "application/javascript;charset=UTF-8")
    @Operation(summary = "聊天窗口脚本")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> widget() throws Exception {
        if (widgetJs == null) {
            try (InputStream in = new ClassPathResource("web-im/widget.js").getInputStream()) {
                widgetJs = IoUtil.read(in, StandardCharsets.UTF_8);
            }
        }
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePublic()).body(widgetJs);
    }

    @GetMapping("/config")
    @Operation(summary = "聊天窗口配置")
    @PermitAll
    @TenantIgnore
    public CommonResult<ChannelWebImService.SiteConfig> config(@RequestParam("k") String k,
                                                               @RequestHeader(value = "Origin", required = false) String origin) {
        return success(channelWebImService.config(k, origin));
    }

    @PostMapping("/send")
    @Operation(summary = "访客发消息")
    @PermitAll
    @TenantIgnore
    public CommonResult<ChannelWebImService.VisitorMessage> send(@RequestBody SendReq req,
                                                                 @RequestHeader(value = "Origin", required = false) String origin) {
        return success(channelWebImService.send(req.getK(), origin, req.getVisitorId(), req.getText(), req.getPage()));
    }

    @GetMapping("/messages")
    @Operation(summary = "访客拉取新消息")
    @PermitAll
    @TenantIgnore
    public CommonResult<List<ChannelWebImService.VisitorMessage>> messages(@RequestParam("k") String k,
                                                                          @RequestParam("v") String visitorId,
                                                                          @RequestParam(value = "after", required = false) Long after,
                                                                          @RequestHeader(value = "Origin", required = false) String origin) {
        return success(channelWebImService.messages(k, origin, visitorId, after));
    }

}
