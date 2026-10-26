package cn.iocoder.yudao.module.channel.controller.admin.social;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.controller.admin.oauth.OAuthResultPage;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.service.social.ChannelSocialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/** 评论渠道（YouTube、TikTok）：授权绑定、手动拉取评论 */
@Tag(name = "管理后台 - YouTube / TikTok 评论渠道")
@RestController
@RequestMapping("/channel/social")
@Slf4j
public class ChannelSocialController {

    @Resource
    private ChannelSocialService channelSocialService;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;

    @GetMapping("/platforms")
    @Operation(summary = "已配置授权应用的平台：youtube、tiktok")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<List<String>> platforms() {
        return success(channelSocialService.enabledPlatforms());
    }

    @GetMapping("/{platform}/authorize-url")
    @Operation(summary = "获取授权地址（hint：TikTok 用户名，仅用于核对）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<String> authorizeUrl(@PathVariable("platform") String platform,
                                             @RequestParam(value = "hint", required = false) String hint) {
        return success(channelSocialService.authorizeUrl(getLoginUserId(), platform, hint));
    }

    @PostMapping("/poll")
    @Operation(summary = "立即拉取一次评论（平时每 3 分钟自动拉取）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:update')")
    public CommonResult<Integer> poll(@RequestParam("id") Long id) {
        ChannelWaAccountDO a = channelWaAccountMapper.selectById(id);
        if (a == null || !ChannelSocialService.isSocial(a.getPlatform())) {
            return success(0);
        }
        return success(channelSocialService.poll(a));
    }

    @GetMapping(value = {"/{platform}/callback", "/{platform}/callback/"}, produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    @Operation(summary = "授权回调（无需登录；租户与成员由 state 确定）")
    @PermitAll
    @TenantIgnore
    public String callback(@PathVariable("platform") String platform,
                           @RequestParam(value = "code", required = false) String code,
                           @RequestParam(value = "auth_code", required = false) String authCode,
                           @RequestParam(value = "state", required = false) String state,
                           @RequestParam(value = "error", required = false) String error) {
        String label = ChannelSocialService.YOUTUBE.equals(platform) ? "YouTube" : "TikTok";
        try {
            String name = channelSocialService.callback(platform, code != null ? code : authCode, state, error);
            return OAuthResultPage.html("cgch-social-oauth", true, label + " 授权成功",
                    "已绑定 " + name + "。系统会定时拉取视频下的新评论，出现在「会话」中。可以关闭此页面。");
        } catch (ServiceException e) {
            return OAuthResultPage.html("cgch-social-oauth", false, label + " 授权失败", e.getMessage());
        } catch (Exception e) {
            log.warn("[callback][{} 授权异常]", platform, e);
            return OAuthResultPage.html("cgch-social-oauth", false, label + " 授权失败", "服务器处理授权时出错，请稍后重试");
        }
    }

}
