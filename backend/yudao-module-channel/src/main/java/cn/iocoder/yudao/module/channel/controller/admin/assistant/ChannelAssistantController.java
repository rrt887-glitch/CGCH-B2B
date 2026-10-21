package cn.iocoder.yudao.module.channel.controller.admin.assistant;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.assistant.ChannelAssistantChatDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.assistant.ChannelAssistantMessageDO;
import cn.iocoder.yudao.module.channel.service.assistant.ChannelAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

@Tag(name = "管理后台 - 对话助手")
@RestController
@RequestMapping("/channel/assistant")
@Validated
public class ChannelAssistantController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private ChannelAssistantService channelAssistantService;

    @PostMapping("/ask")
    @Operation(summary = "提问；不传 chatId 时新建会话。返回回答、查询步骤与消耗智点")
    @PreAuthorize("@ss.hasPermission('channel:assistant')")
    public CommonResult<ChannelAssistantService.Reply> ask(@RequestBody Map<String, Object> req) {
        Object chatId = req.get("chatId");
        return success(channelAssistantService.ask(getLoginUserId(),
                chatId == null || String.valueOf(chatId).isBlank() ? null : Long.valueOf(String.valueOf(chatId)),
                (String) req.get("question")));
    }

    @GetMapping("/chats")
    @Operation(summary = "我的会话")
    @PreAuthorize("@ss.hasPermission('channel:assistant')")
    public CommonResult<List<Map<String, Object>>> chats() {
        return success(channelAssistantService.chats(getLoginUserId()).stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("title", c.getTitle());
            m.put("updateTime", c.getUpdateTime() == null ? null : c.getUpdateTime().format(TIME));
            return m;
        }).toList());
    }

    @GetMapping("/messages")
    @Operation(summary = "会话消息")
    @PreAuthorize("@ss.hasPermission('channel:assistant')")
    public CommonResult<List<Map<String, Object>>> messages(@RequestParam("chatId") Long chatId) {
        return success(channelAssistantService.messages(getLoginUserId(), chatId).stream().map(m -> {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("id", m.getId());
            x.put("role", m.getRole());
            x.put("content", m.getContent());
            x.put("steps", m.getSteps() == null ? List.of() : JsonUtils.parseArray(m.getSteps(), Map.class));
            x.put("credits", m.getCredits());
            x.put("createTime", m.getCreateTime() == null ? null : m.getCreateTime().format(TIME));
            return x;
        }).toList());
    }

    @DeleteMapping("/chat/delete")
    @Operation(summary = "删除会话")
    @PreAuthorize("@ss.hasPermission('channel:assistant')")
    public CommonResult<Boolean> delete(@RequestParam("chatId") Long chatId) {
        channelAssistantService.deleteChat(getLoginUserId(), chatId);
        return success(true);
    }

}
