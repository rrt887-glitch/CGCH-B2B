package cn.iocoder.yudao.module.channel.controller.admin.whatsapp;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.*;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
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

@Tag(name = "管理后台 - 渠道：WhatsApp 会话与消息")
@RestController
@RequestMapping("/channel/wa")
@Validated
public class ChannelWaMessageController {

    @Resource
    private ChannelWhatsAppService whatsAppService;

    @GetMapping("/conversation/page")
    @Operation(summary = "会话列表，按最后消息时间倒序")
    @PreAuthorize("@ss.hasPermission('channel:wa-message:query')")
    public CommonResult<PageResult<ChannelWaConversationRespVO>> conversationPage(@Valid ChannelWaConversationPageReqVO req) {
        return success(whatsAppService.getConversationPage(getLoginUserId(), req));
    }

    @GetMapping("/conversation/get")
    @Operation(summary = "会话详情（含 24 小时窗口状态）")
    @Parameter(name = "id", required = true)
    @PreAuthorize("@ss.hasPermission('channel:wa-message:query')")
    public CommonResult<ChannelWaConversationRespVO> conversation(@RequestParam("id") Long id) {
        return success(whatsAppService.getConversation(getLoginUserId(), id));
    }

    @GetMapping("/message/list")
    @Operation(summary = "会话消息，时间正序；不带 beforeId 时清零未读")
    @PreAuthorize("@ss.hasPermission('channel:wa-message:query')")
    public CommonResult<List<ChannelWaMessageRespVO>> messages(@RequestParam("conversationId") Long conversationId,
                                                               @RequestParam(value = "beforeId", required = false) Long beforeId,
                                                               @RequestParam(value = "limit", required = false) Integer limit) {
        return success(whatsAppService.getMessages(getLoginUserId(), conversationId, beforeId, limit));
    }

    @PostMapping("/message/send")
    @Operation(summary = "发送文本（24 小时窗口内）或模板消息")
    @PreAuthorize("@ss.hasPermission('channel:wa-message:send')")
    public CommonResult<ChannelWaMessageRespVO> send(@Valid @RequestBody ChannelWaSendReqVO req) {
        return success(whatsAppService.send(getLoginUserId(), req));
    }

    @GetMapping("/message/media")
    @Operation(summary = "取媒体文件地址；收到的媒体首次查看时从 Meta 下载并保存")
    @Parameter(name = "id", description = "消息编号", required = true)
    @PreAuthorize("@ss.hasPermission('channel:wa-message:query')")
    public CommonResult<ChannelWaMessageRespVO> media(@RequestParam("id") Long id) {
        return success(whatsAppService.getMedia(getLoginUserId(), id));
    }

    @PostMapping("/message/send-media")
    @Operation(summary = "发送图片、视频、语音或文件（24 小时窗口内）")
    @PreAuthorize("@ss.hasPermission('channel:wa-message:send')")
    public CommonResult<ChannelWaMessageRespVO> sendMedia(@RequestParam("conversationId") Long conversationId,
                                                          @RequestParam("file") MultipartFile file,
                                                          @RequestParam(value = "caption", required = false) String caption) throws Exception {
        return success(whatsAppService.sendMedia(getLoginUserId(), conversationId, file, caption));
    }

}
