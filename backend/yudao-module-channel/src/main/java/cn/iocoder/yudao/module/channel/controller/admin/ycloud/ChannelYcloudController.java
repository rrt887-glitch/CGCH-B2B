package cn.iocoder.yudao.module.channel.controller.admin.ycloud;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.service.ycloud.ChannelYcloudService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * WhatsApp 经 YCloud 接入：创建扫码绑定链接、查询绑定结果；接收 YCloud 推送（无需登录，校验签名）。
 * 在 YCloud 控制台 → Developers → Webhook 添加接收地址 https://你的域名/admin-api/channel/ycloud/webhook。
 */
@Tag(name = "管理后台 - WhatsApp（YCloud）")
@RestController
@RequestMapping("/channel/ycloud")
public class ChannelYcloudController {

    @Resource
    private ChannelYcloudService channelYcloudService;

    @PostMapping("/onboarding-link")
    @Operation(summary = "创建扫码绑定链接（mode=coexist 共存模式，new 开通新号码）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<ChannelYcloudService.Link> link(@RequestBody Map<String, String> req) {
        return success(channelYcloudService.createLink(getLoginUserId(), !"new".equals(req.get("mode"))));
    }

    @GetMapping("/onboarding-result")
    @Operation(summary = "绑定结果：完成后返回账号编号与号码")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Map<String, Object>> result(@RequestParam("ref") String ref) {
        ChannelWaAccountDO a = channelYcloudService.result(getLoginUserId(), ref);
        if (a == null) {
            return success(null);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", a.getId());
        r.put("displayPhone", a.getDisplayPhone());
        return success(r);
    }

    @PostMapping("/webhook")
    @Operation(summary = "接收 YCloud 推送")
    @PermitAll
    @TenantIgnore
    public ResponseEntity<String> webhook(@RequestHeader(value = "YCloud-Signature", required = false) String signature,
                                          @RequestBody byte[] body) {
        return channelYcloudService.handleWebhook(body, signature) ? ResponseEntity.ok("ok")
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("invalid signature");
    }

}
