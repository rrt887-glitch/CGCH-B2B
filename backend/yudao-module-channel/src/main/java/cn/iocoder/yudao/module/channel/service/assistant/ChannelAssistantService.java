package cn.iocoder.yudao.module.channel.service.assistant;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.assistant.ChannelAssistantChatDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.assistant.ChannelAssistantMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.assistant.ChannelAssistantChatMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.assistant.ChannelAssistantMessageMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 对话助手：业务员用自然语言提问，模型按需调用只读工具查询 CRM、消息和统计，再根据结果回答。
 */
@Service
@Slf4j
public class ChannelAssistantService {

    private static final ObjectMapper JSON = new ObjectMapper();
    /** 一次提问最多调用几轮工具 */
    private static final int MAX_ROUNDS = 5;
    /** 带给模型的历史消息条数 */
    private static final int HISTORY = 10;

    @Resource
    private ChannelAssistantChatMapper channelAssistantChatMapper;
    @Resource
    private ChannelAssistantMessageMapper channelAssistantMessageMapper;
    @Resource
    private ChannelAssistantTools channelAssistantTools;
    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private AdminUserApi adminUserApi;

    public record Reply(Long chatId, Long messageId, String content, List<Map<String, String>> steps, long credits) {
    }

    public Reply ask(Long userId, Long chatId, String question) {
        if (StrUtil.isBlank(question)) {
            throw exception(ASSISTANT_INVALID, "请输入问题");
        }
        ChannelAiConfigDO cfg = channelAiService.getConfig();
        if (StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
            throw exception(ASSISTANT_INVALID, "请先在数字员工设置中配置大模型");
        }
        if (!channelCreditService.hasBalance()) {
            throw exception(AI_CREDIT_INSUFFICIENT);
        }
        ChannelAssistantChatDO chat = chatId == null ? null : getChat(userId, chatId);
        if (chat == null) {
            chat = ChannelAssistantChatDO.builder().userId(userId).title(StrUtil.maxLength(question.trim(), 60)).build();
            channelAssistantChatMapper.insert(chat);
        }
        List<ChannelAssistantMessageDO> history = channelAssistantMessageMapper.selectListByChatId(chat.getId());
        channelAssistantMessageMapper.insert(ChannelAssistantMessageDO.builder().chatId(chat.getId())
                .role(ChannelAssistantMessageDO.ROLE_USER).content(StrUtil.maxLength(question.trim(), 2000)).credits(0L).build());

        ArrayNode messages = JSON.createArrayNode();
        messages.addObject().put("role", "system").put("content", systemPrompt(userId));
        for (ChannelAssistantMessageDO m : history.subList(Math.max(0, history.size() - HISTORY), history.size())) {
            messages.addObject().put("role", m.getRole()).put("content", m.getContent());
        }
        messages.addObject().put("role", "user").put("content", question.trim());

        ChannelAssistantTools.Ctx ctx = channelAssistantTools.context(userId);
        ArrayNode tools = channelAssistantTools.definitions();
        List<Map<String, String>> steps = new ArrayList<>();
        int tokens = 0;
        String answer = null;
        try {
            for (int round = 0; round <= MAX_ROUNDS && answer == null; round++) {
                LlmClient.ToolTurn t = LlmClient.chatWithTools(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(),
                        messages, round < MAX_ROUNDS ? tools : null, 0.2);
                tokens += t.promptTokens() + t.completionTokens();
                if (t.toolCalls().isEmpty()) {
                    answer = t.content();
                    break;
                }
                messages.add(t.assistantMessage());
                for (JsonNode call : t.toolCalls()) {
                    String name = call.path("function").path("name").asText();
                    String args = call.path("function").path("arguments").asText("{}");
                    String result;
                    try {
                        result = channelAssistantTools.call(ctx, name, args);
                    } catch (Exception e) {
                        log.warn("[ask][工具 {} 执行失败] {}", name, e.getMessage());
                        result = "{\"error\":\"查询失败\"}";
                    }
                    steps.add(Map.of("tool", name, "args", StrUtil.maxLength(args, 300)));
                    messages.addObject().put("role", "tool").put("tool_call_id", call.path("id").asText())
                            .put("name", name).put("content", result);
                }
            }
        } catch (LlmClient.LlmException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
        if (StrUtil.isBlank(answer)) {
            answer = "抱歉，这个问题我查了几次还没有得到结论，请换个问法或说得更具体一些。";
        }
        long credits = channelCreditService.consume("assistant", chat.getId(), userId, Math.max(tokens, 1), "对话助手");
        ChannelAssistantMessageDO reply = ChannelAssistantMessageDO.builder().chatId(chat.getId())
                .role(ChannelAssistantMessageDO.ROLE_ASSISTANT).content(answer).steps(JsonUtils.toJsonString(steps)).credits(credits).build();
        channelAssistantMessageMapper.insert(reply);
        channelAssistantChatMapper.updateById(new ChannelAssistantChatDO().setId(chat.getId()).setUpdateTime(LocalDateTime.now()));
        return new Reply(chat.getId(), reply.getId(), answer, steps, credits);
    }

    private String systemPrompt(Long userId) {
        AdminUserRespDTO u = adminUserApi.getUser(userId);
        String name = u == null ? "业务员" : StrUtil.blankToDefault(u.getNickname(), u.getUsername());
        return "你是外贸企业 CRM 里的工作助手，正在帮业务员「" + name + "」（用户编号 " + userId + "）处理日常工作。今天是 "
                + LocalDate.now() + "（" + LocalDate.now().getDayOfWeek() + "）。\n"
                + "规则：\n1. 涉及客户、消息、统计等数据时，必须先调用工具查询，只根据工具返回的结果回答，不要编造。\n"
                + "2. 工具只返回当前用户有权查看的数据；查不到时如实说明。\n"
                + "3. 你只能查询，不能修改数据或发送消息；需要操作时告诉用户去哪个页面处理。\n"
                + "4. 用简体中文回答，简洁、有条理，先给结论再列要点；提到客户时写出客户名称。";
    }

    // ==================== 会话 ====================

    private ChannelAssistantChatDO getChat(Long userId, Long chatId) {
        ChannelAssistantChatDO c = channelAssistantChatMapper.selectById(chatId);
        if (c == null || !Objects.equals(c.getUserId(), userId)) {
            throw exception(ASSISTANT_CHAT_NOT_EXISTS);
        }
        return c;
    }

    public List<ChannelAssistantChatDO> chats(Long userId) {
        return channelAssistantChatMapper.selectListByUserId(userId);
    }

    public List<ChannelAssistantMessageDO> messages(Long userId, Long chatId) {
        getChat(userId, chatId);
        return channelAssistantMessageMapper.selectListByChatId(chatId);
    }

    public void deleteChat(Long userId, Long chatId) {
        getChat(userId, chatId);
        channelAssistantMessageMapper.delete(ChannelAssistantMessageDO::getChatId, chatId);
        channelAssistantChatMapper.deleteById(chatId);
    }

}
