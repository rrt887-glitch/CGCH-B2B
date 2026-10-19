package cn.iocoder.yudao.module.channel.controller.admin.mail;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailAccountRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailAccountSaveReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
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

@Tag(name = "管理后台 - 渠道：邮箱账号")
@RestController
@RequestMapping("/channel/mail-account")
@Validated
public class ChannelMailAccountController {

    @Resource
    private ChannelMailService mailService;

    @PostMapping("/create")
    @Operation(summary = "绑定邮箱")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:create')")
    public CommonResult<Long> create(@Valid @RequestBody ChannelMailAccountSaveReqVO req) {
        return success(mailService.createAccount(getLoginUserId(), req));
    }

    @PutMapping("/update")
    @Operation(summary = "修改邮箱配置")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:update')")
    public CommonResult<Boolean> update(@Valid @RequestBody ChannelMailAccountSaveReqVO req) {
        mailService.updateAccount(getLoginUserId(), req);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "解绑邮箱，同时删除已同步的邮件")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('channel:mail-account:delete')")
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        mailService.deleteAccount(getLoginUserId(), id);
        return success(true);
    }

    @GetMapping("/list")
    @Operation(summary = "我的邮箱列表")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:query')")
    public CommonResult<List<ChannelMailAccountRespVO>> list() {
        return success(mailService.listAccounts(getLoginUserId()));
    }

    @PostMapping("/test")
    @Operation(summary = "测试 IMAP 与 SMTP 连接；修改时可不填密码")
    @PreAuthorize("@ss.hasPermission('channel:mail-account:create')")
    public CommonResult<Boolean> test(@Valid @RequestBody ChannelMailAccountSaveReqVO req) {
        mailService.testAccount(getLoginUserId(), req);
        return success(true);
    }

    @PostMapping("/sync")
    @Operation(summary = "立即收信，返回新收到的邮件数")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('channel:mail-message:query')")
    public CommonResult<Integer> sync(@RequestParam("id") Long id) {
        ChannelMailAccountDO account = mailService.validateAccount(getLoginUserId(), id);
        return success(mailService.syncAccount(account));
    }

}
