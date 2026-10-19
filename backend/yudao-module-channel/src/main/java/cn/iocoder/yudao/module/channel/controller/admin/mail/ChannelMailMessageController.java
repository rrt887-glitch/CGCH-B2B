package cn.iocoder.yudao.module.channel.controller.admin.mail;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailAttachmentRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailMessagePageReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailMessageRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailSendReqVO;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 渠道：邮件")
@RestController
@RequestMapping("/channel/mail-message")
@Validated
public class ChannelMailMessageController {

    @Resource
    private ChannelMailService mailService;

    @GetMapping("/page")
    @Operation(summary = "邮件列表（不含正文）")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:query')")
    public CommonResult<PageResult<ChannelMailMessageRespVO>> page(@Valid ChannelMailMessagePageReqVO req) {
        return success(mailService.getMessagePage(getLoginUserId(), req));
    }

    @GetMapping("/get")
    @Operation(summary = "邮件详情，同时标为已读")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('channel:mail-message:query')")
    public CommonResult<ChannelMailMessageRespVO> get(@RequestParam("id") Long id) {
        return success(mailService.readMessage(getLoginUserId(), id));
    }

    @GetMapping("/thread")
    @Operation(summary = "同一会话的全部往来邮件")
    @Parameter(name = "id", description = "会话中任意一封邮件的编号", required = true)
    @PreAuthorize("@ss.hasPermission('channel:mail-message:query')")
    public CommonResult<List<ChannelMailMessageRespVO>> thread(@RequestParam("id") Long id) {
        return success(mailService.getThread(getLoginUserId(), id));
    }

    @PostMapping("/send")
    @Operation(summary = "发送或回复邮件，返回已发送邮件的编号")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:send')")
    public CommonResult<Long> send(@Valid @RequestBody ChannelMailSendReqVO req) {
        return success(mailService.send(getLoginUserId(), req));
    }

    @PostMapping("/upload-attachment")
    @Operation(summary = "上传待发送的附件（单个不超过 25MB），返回附件编号")
    @PreAuthorize("@ss.hasPermission('channel:mail-message:send')")
    public CommonResult<ChannelMailAttachmentRespVO> uploadAttachment(@RequestParam("file") MultipartFile file) throws Exception {
        return success(mailService.uploadAttachment(getLoginUserId(), file));
    }

    @DeleteMapping("/trash")
    @Operation(summary = "移到已删除")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('channel:mail-message:delete')")
    public CommonResult<Boolean> trash(@RequestParam("id") Long id) {
        mailService.moveToTrash(getLoginUserId(), id);
        return success(true);
    }

}
