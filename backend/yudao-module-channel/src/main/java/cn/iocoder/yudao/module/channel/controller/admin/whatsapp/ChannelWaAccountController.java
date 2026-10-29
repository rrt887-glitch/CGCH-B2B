package cn.iocoder.yudao.module.channel.controller.admin.whatsapp;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaAccountRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaAccountSaveReqVO;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 渠道：WhatsApp 号码")
@RestController
@RequestMapping("/channel/wa-account")
@Validated
public class ChannelWaAccountController {

    @Resource
    private ChannelWhatsAppService whatsAppService;

    @PostMapping("/create")
    @Operation(summary = "绑定 WhatsApp 号码（会用令牌调用一次 Graph API 校验）")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:create')")
    public CommonResult<Long> create(@Valid @RequestBody ChannelWaAccountSaveReqVO req) {
        return success(whatsAppService.createAccount(getLoginUserId(), req));
    }

    @PutMapping("/update")
    @Operation(summary = "修改号码配置；令牌、密钥不填表示不变")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ChannelWaAccountSaveReqVO req) {
        whatsAppService.updateAccount(getLoginUserId(), req);
        return success(true);
    }

    public record AssigneesReq(Long id, java.util.List<Long> userIds) {
    }

    @PutMapping("/assignees")
    @Operation(summary = "设置接待成员：这个号码的新客户按顺序轮流分给他们；为空表示都由自己处理")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:update')")
    public CommonResult<Boolean> assignees(@RequestBody AssigneesReq req) {
        whatsAppService.setAssignees(getLoginUserId(), req.id(), req.userIds());
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "解绑号码，同时删除会话与消息")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('channel:wa-account:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        whatsAppService.deleteAccount(getLoginUserId(), id);
        return success(true);
    }

    @PostMapping("/sync-history")
    @Operation(summary = "同步 Facebook 主页 / Instagram 最近的聊天记录，返回新导入的消息数")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('channel:wa-account:update')")
    public CommonResult<Integer> syncHistory(@RequestParam("id") Long id) {
        return success(whatsAppService.importMessagingHistory(whatsAppService.validateAccount(getLoginUserId(), id)));
    }

    @GetMapping("/list")
    @Operation(summary = "我的 WhatsApp 号码")
    @PreAuthorize("@ss.hasPermission('channel:wa-account:query')")
    public CommonResult<List<ChannelWaAccountRespVO>> list() {
        return success(whatsAppService.listAccounts(getLoginUserId()));
    }

    @GetMapping("/templates")
    @Operation(summary = "该号码所属 WABA 下的消息模板（实时从 Meta 查询）")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('channel:wa-account:query')")
    public CommonResult<JsonNode> templates(@RequestParam("id") Long id) {
        return success(whatsAppService.listTemplates(getLoginUserId(), id));
    }

}
