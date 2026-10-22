package cn.iocoder.yudao.module.channel.controller.admin.oauth;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.service.oauth.ChannelMailOAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 邮箱 OAuth 授权（Gmail / Outlook）")
@RestController
@RequestMapping("/channel/oauth")
@Validated
@Slf4j
public class ChannelMailOAuthController {

    @Resource
    private ChannelMailOAuthService channelMailOAuthService;

    @GetMapping("/providers")
    @Operation(summary = "已配置授权应用的服务商：google、microsoft")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:create')")
    public CommonResult<List<String>> providers() {
        return success(channelMailOAuthService.providers());
    }

    @GetMapping("/{provider}/authorize-url")
    @Operation(summary = "获取授权地址，前端在新窗口打开")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:create')")
    public CommonResult<String> authorizeUrl(@PathVariable("provider") String provider) {
        return success(channelMailOAuthService.authorizeUrl(getLoginUserId(), provider));
    }

    @GetMapping(value = "/{provider}/callback", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "授权回调（由 Google / Microsoft 跳转，无需登录；租户与用户由 state 确定）")
    @PermitAll
    @TenantIgnore
    public String callback(@PathVariable("provider") String provider,
                           @RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error) {
        String title;
        String detail;
        boolean ok;
        try {
            String email = channelMailOAuthService.callback(provider, code, state, error);
            ok = true;
            title = "邮箱授权成功";
            detail = "已绑定 " + email + "，系统会在几分钟内开始收信。可以关闭此页面。";
        } catch (ServiceException e) {
            ok = false;
            title = "邮箱授权失败";
            detail = e.getMessage();
        } catch (Exception e) {
            log.warn("[callback][{} 授权异常]", provider, e);
            ok = false;
            title = "邮箱授权失败";
            detail = "服务器处理授权时出错，请稍后重试";
        }
        String t = HtmlUtils.htmlEscape(title), d = HtmlUtils.htmlEscape(StrUtil.nullToEmpty(detail));
        return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>" + t + "</title></head><body style=\"font-family:system-ui,sans-serif;padding:48px;text-align:center\">"
                + "<h2 style=\"color:" + (ok ? "#1a7f37" : "#cf222e") + "\">" + t + "</h2><p>" + d + "</p>"
                + "<script>try{window.opener&&window.opener.postMessage({type:'cgch-mail-oauth',ok:" + ok + "},'*');"
                + (ok ? "setTimeout(function(){window.close()},1500);" : "") + "}catch(e){}</script></body></html>";
    }

}
