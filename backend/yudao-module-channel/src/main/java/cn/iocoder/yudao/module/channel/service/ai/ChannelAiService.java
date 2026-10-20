package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiDraftDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiForbiddenWordDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiConfigMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiDraftMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiForbiddenWordMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiKnowledgeMapper;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailSendReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaSendReqVO;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import cn.iocoder.yudao.module.channel.service.mail.HtmlText;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * AI 数字员工：按知识库生成回复，起草或自动发出；遇到转人工、违禁词、业务员正在跟进等情况只留草稿。
 */
@Service
@Slf4j
public class ChannelAiService {

    private static final double TEMPERATURE = 0.3;
    private static final int MAIL_HISTORY = 6;
    private static final int WA_HISTORY = 12;
    private static final int HISTORY_CHARS = 2000;
    /** 系统退信、通知类邮件不回复 */
    private static final Pattern NO_REPLY = Pattern.compile("(?i)(no-?reply|mailer-daemon|postmaster|notification|bounce)");

    @Resource
    private ChannelAiConfigMapper channelAiConfigMapper;
    @Resource
    private ChannelAiKnowledgeMapper channelAiKnowledgeMapper;
    @Resource
    private ChannelAiForbiddenWordMapper channelAiForbiddenWordMapper;
    @Resource
    private ChannelAiDraftMapper channelAiDraftMapper;
    @Resource
    private ChannelMailService mailService;
    @Resource
    private ChannelWhatsAppService whatsAppService;
    @Resource
    private CrmCustomerMapper customerMapper;

    // ==================== 配置 ====================

    public ChannelAiConfigDO getConfig() {
        ChannelAiConfigDO c = channelAiConfigMapper.selectCurrent();
        if (c == null) {
            c = ChannelAiConfigDO.builder().agentName("Sales Assistant").personality("professional")
                    .defaultLanguage("English").mailMode(ChannelAiConfigDO.MODE_OFF).waMode(ChannelAiConfigDO.MODE_OFF)
                    .humanPauseHours(12).build();
        }
        return c;
    }

    public void saveConfig(ChannelAiConfigDO req) {
        ChannelAiConfigDO old = channelAiConfigMapper.selectCurrent();
        req.setMailMode(normalizeMode(req.getMailMode()));
        req.setWaMode(normalizeMode(req.getWaMode()));
        if (req.getHumanPauseHours() == null || req.getHumanPauseHours() < 0) {
            req.setHumanPauseHours(12);
        }
        if (StrUtil.isBlank(req.getLlmApiKey())) {
            req.setLlmApiKey(null); // 不填表示不修改
        }
        if (old == null) {
            req.setId(null);
            channelAiConfigMapper.insert(req);
        } else {
            req.setId(old.getId());
            channelAiConfigMapper.updateById(req);
        }
    }

    private static String normalizeMode(String m) {
        return List.of(ChannelAiConfigDO.MODE_DRAFT, ChannelAiConfigDO.MODE_AUTO).contains(m) ? m : ChannelAiConfigDO.MODE_OFF;
    }

    /** 用一句话测试大模型配置；req 中 Key 为空时用已保存的 */
    public String testConfig(ChannelAiConfigDO req) {
        ChannelAiConfigDO saved = channelAiConfigMapper.selectCurrent();
        String key = StrUtil.isNotBlank(req.getLlmApiKey()) ? req.getLlmApiKey() : saved == null ? null : saved.getLlmApiKey();
        try {
            LlmClient.Result r = LlmClient.chat(req.getLlmBaseUrl(), key, req.getLlmModel(),
                    List.of(LlmClient.Message.user("Reply with the single word: OK")), 0);
            return r.content();
        } catch (LlmClient.LlmException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
    }

    // ==================== 知识库与违禁词 ====================

    public List<ChannelAiKnowledgeDO> listKnowledge(String type) {
        return channelAiKnowledgeMapper.selectListByType(StrUtil.emptyToNull(type));
    }

    public Long saveKnowledge(ChannelAiKnowledgeDO k) {
        if (StrUtil.isBlank(k.getContent())) {
            throw exception(AI_KNOWLEDGE_EMPTY);
        }
        if (!List.of(ChannelAiKnowledgeDO.TYPE_COMPANY, ChannelAiKnowledgeDO.TYPE_PRODUCT, ChannelAiKnowledgeDO.TYPE_FAQ).contains(k.getType())) {
            k.setType(ChannelAiKnowledgeDO.TYPE_FAQ);
        }
        if (k.getEnabled() == null) {
            k.setEnabled(true);
        }
        if (k.getSort() == null) {
            k.setSort(0);
        }
        if (k.getId() == null) {
            channelAiKnowledgeMapper.insert(k);
        } else {
            if (channelAiKnowledgeMapper.selectById(k.getId()) == null) {
                throw exception(AI_KNOWLEDGE_NOT_EXISTS);
            }
            channelAiKnowledgeMapper.updateById(k);
        }
        return k.getId();
    }

    public void deleteKnowledge(Long id) {
        channelAiKnowledgeMapper.deleteById(id);
    }

    public List<ChannelAiForbiddenWordDO> listForbidden() {
        return channelAiForbiddenWordMapper.selectList();
    }

    public Long saveForbidden(ChannelAiForbiddenWordDO w) {
        if (StrUtil.isBlank(w.getWord())) {
            throw exception(AI_FORBIDDEN_EMPTY);
        }
        w.setWord(w.getWord().trim());
        ChannelAiForbiddenWordDO same = channelAiForbiddenWordMapper.selectByWord(w.getWord());
        if (same != null && !Objects.equals(same.getId(), w.getId())) {
            throw exception(AI_FORBIDDEN_EXISTS);
        }
        if (w.getEnabled() == null) {
            w.setEnabled(true);
        }
        if (w.getId() == null) {
            channelAiForbiddenWordMapper.insert(w);
        } else {
            channelAiForbiddenWordMapper.updateById(w);
        }
        return w.getId();
    }

    public void deleteForbidden(Long id) {
        channelAiForbiddenWordMapper.deleteById(id);
    }

    // ==================== 生成 ====================

    /** 一次生成的结果 */
    public record Generated(String content, boolean handoff, String forbidden, int promptTokens, int completionTokens, String model) {
    }

    private Generated generate(ChannelAiConfigDO cfg, String channel, String customerText, List<LlmClient.Message> history) {
        String system = AiPromptBuilder.systemPrompt(cfg, channelAiKnowledgeMapper.selectEnabled(), customerText, channel);
        List<LlmClient.Message> messages = new ArrayList<>();
        messages.add(LlmClient.Message.system(system));
        messages.addAll(history);
        LlmClient.Result r;
        try {
            r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(), messages, TEMPERATURE);
        } catch (LlmClient.LlmException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
        boolean handoff = AiPromptBuilder.isHandoff(r.content());
        String content = handoff ? "" : r.content();
        String hit = handoff ? null : AiPromptBuilder.firstForbidden(content,
                channelAiForbiddenWordMapper.selectEnabled().stream().map(ChannelAiForbiddenWordDO::getWord).toList());
        return new Generated(content, handoff, hit, r.promptTokens(), r.completionTokens(), cfg.getLlmModel());
    }

    /** 邮件往来 → 对话历史：客户来信为 user，我方回复为 assistant */
    private List<LlmClient.Message> mailHistory(ChannelMailMessageDO m) {
        List<ChannelMailMessageDO> thread = mailService.getThreadInternal(m);
        List<ChannelMailMessageDO> recent = thread.subList(Math.max(0, thread.size() - MAIL_HISTORY), thread.size());
        List<LlmClient.Message> list = new ArrayList<>();
        for (ChannelMailMessageDO t : recent) {
            String body = StrUtil.isNotBlank(t.getTextBody()) ? t.getTextBody() : HtmlText.toText(t.getHtmlBody());
            body = HtmlText.snippet(body, null, HISTORY_CHARS);
            String text = "主题：" + StrUtil.nullToEmpty(t.getSubject()) + "\n" + body;
            list.add(t.getDirection() == ChannelMailMessageDO.DIRECTION_IN ? LlmClient.Message.user(text) : LlmClient.Message.assistant(body));
        }
        if (list.isEmpty() || !"user".equals(list.get(list.size() - 1).role())) {
            list.add(LlmClient.Message.user("主题：" + StrUtil.nullToEmpty(m.getSubject()) + "\n"
                    + HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), HISTORY_CHARS)));
        }
        return list;
    }

    private List<LlmClient.Message> waHistory(Long conversationId) {
        List<LlmClient.Message> list = new ArrayList<>();
        for (ChannelWaMessageDO t : whatsAppService.getRecentMessagesInternal(conversationId, WA_HISTORY)) {
            String text = StrUtil.blankToDefault(t.getTextBody(), "[" + t.getType() + "]");
            if (list.size() > 0 && list.get(list.size() - 1).role().equals(t.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? "user" : "assistant")) {
                LlmClient.Message last = list.remove(list.size() - 1); // 连续同一方的消息合并
                list.add(new LlmClient.Message(last.role(), last.content() + "\n" + text));
            } else {
                list.add(t.getDirection() == ChannelWaMessageDO.DIRECTION_IN ? LlmClient.Message.user(text) : LlmClient.Message.assistant(text));
            }
        }
        return list;
    }

    private static String lastUserText(List<LlmClient.Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if ("user".equals(history.get(i).role())) {
                return history.get(i).content();
            }
        }
        return "";
    }

    // ==================== 手动起草（工作台「AI 起草」按钮） ====================

    public ChannelAiDraftDO draftForMail(Long userId, Long messageId) {
        ChannelMailMessageDO m = mailService.getMessage(userId, messageId);
        ChannelAiConfigDO cfg = getConfig();
        List<LlmClient.Message> history = mailHistory(m);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_MAIL, lastUserText(history), history);
        return saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), withSignature(g, cfg), g, ChannelAiDraftDO.STATUS_PENDING,
                g.handoff() ? ChannelAiDraftDO.REASON_HANDOFF : g.forbidden() != null ? ChannelAiDraftDO.REASON_FORBIDDEN : ChannelAiDraftDO.REASON_MANUAL,
                g.forbidden());
    }

    public ChannelAiDraftDO draftForWhatsApp(Long userId, Long conversationId) {
        whatsAppService.getConversation(userId, conversationId); // 校验归属
        ChannelAiConfigDO cfg = getConfig();
        List<LlmClient.Message> history = waHistory(conversationId);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(history), history);
        return saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING,
                g.handoff() ? ChannelAiDraftDO.REASON_HANDOFF : g.forbidden() != null ? ChannelAiDraftDO.REASON_FORBIDDEN : ChannelAiDraftDO.REASON_MANUAL,
                g.forbidden());
    }

    private static String withSignature(Generated g, ChannelAiConfigDO cfg) {
        if (g.handoff() || StrUtil.isBlank(cfg.getMailSignature())) {
            return g.content();
        }
        return g.content() + "\n\n" + cfg.getMailSignature().trim();
    }

    private ChannelAiDraftDO saveDraft(String channel, Long refId, String content, Generated g, String status,
                                       String reason, String detail) {
        ChannelAiDraftDO d = ChannelAiDraftDO.builder().channel(channel).refId(refId).content(content).status(status)
                .reason(reason).reasonDetail(StrUtil.maxLength(detail, 250))
                .promptTokens(g.promptTokens()).completionTokens(g.completionTokens()).model(g.model()).build();
        channelAiDraftMapper.insert(d);
        return d;
    }

    public ChannelAiDraftDO getPendingDraft(String channel, Long refId) {
        return channelAiDraftMapper.selectLatestPending(channel, refId);
    }

    public PageResult<ChannelAiDraftDO> getDraftPage(PageParam page, String channel, String status) {
        return channelAiDraftMapper.selectPage(page, channel, status);
    }

    /** 业务员采用（已发送）或放弃草稿 */
    public void closeDraft(Long id, boolean used) {
        ChannelAiDraftDO d = channelAiDraftMapper.selectById(id);
        if (d == null) {
            throw exception(AI_DRAFT_NOT_EXISTS);
        }
        channelAiDraftMapper.updateById(new ChannelAiDraftDO().setId(id)
                .setStatus(used ? ChannelAiDraftDO.STATUS_SENT : ChannelAiDraftDO.STATUS_DISCARDED));
    }

    // ==================== 自动处理（收到新消息时，已在对应租户上下文中调用） ====================

    public void handleMailInbound(Long messageId) {
        ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
        if (cfg == null || ChannelAiConfigDO.MODE_OFF.equals(cfg.getMailMode())) {
            return;
        }
        ChannelMailMessageDO m = mailService.getMessageInternal(messageId);
        if (m == null || m.getDirection() != ChannelMailMessageDO.DIRECTION_IN || NO_REPLY.matcher(StrUtil.nullToEmpty(m.getFromAddress())).find()) {
            return;
        }
        ChannelMailAccountDO account = mailService.getAccountInternal(m.getAccountId());
        if (account == null || StrUtil.equalsIgnoreCase(account.getEmail(), m.getFromAddress())) {
            return;
        }
        List<LlmClient.Message> history = mailHistory(m);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_MAIL, lastUserText(history), history);
        String content = withSignature(g, cfg);
        String reason = blockReason(cfg.getMailMode(), g, m.getCustomerId(),
                humanRepliedRecently(mailService.getThreadInternal(m).stream()
                        .filter(t -> t.getDirection() == ChannelMailMessageDO.DIRECTION_OUT && !Boolean.TRUE.equals(t.getAiGenerated()))
                        .map(ChannelMailMessageDO::getSentTime).toList(), cfg));
        if (reason != null) {
            saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), content, g, ChannelAiDraftDO.STATUS_PENDING, reason, g.forbidden());
            return;
        }
        ChannelMailSendReqVO req = new ChannelMailSendReqVO();
        req.setAccountId(account.getId());
        req.setTo(List.of(m.getFromAddress()));
        req.setSubject(StrUtil.startWithIgnoreCase(m.getSubject(), "re:") ? m.getSubject() : "Re: " + StrUtil.nullToEmpty(m.getSubject()));
        req.setText(content);
        req.setReplyToId(m.getId());
        try {
            mailService.send(account.getUserId(), req, true);
            saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), content, g, ChannelAiDraftDO.STATUS_SENT, null, null);
        } catch (Exception e) {
            saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), content, g, ChannelAiDraftDO.STATUS_PENDING,
                    ChannelAiDraftDO.REASON_SEND_FAILED, e.getMessage());
        }
    }

    /**
     * @param messageId 触发本次处理的消息；如果之后客户又发了新消息，本次放弃，交给最后一条处理（合并连发）
     */
    public void handleWhatsAppInbound(Long conversationId, Long messageId) {
        ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
        if (cfg == null || ChannelAiConfigDO.MODE_OFF.equals(cfg.getWaMode())) {
            return;
        }
        ChannelWaConversationDO conv = whatsAppService.getConversationInternal(conversationId);
        if (conv == null) {
            return;
        }
        List<ChannelWaMessageDO> recent = whatsAppService.getRecentMessagesInternal(conversationId, WA_HISTORY);
        Optional<ChannelWaMessageDO> lastIn = recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                .reduce((a, b) -> b);
        if (lastIn.isEmpty() || !lastIn.get().getId().equals(messageId)) {
            return; // 有更新的消息，等它触发
        }
        if (!recent.isEmpty() && recent.get(recent.size() - 1).getDirection() == ChannelWaMessageDO.DIRECTION_OUT) {
            return; // 已经有人回过了
        }
        ChannelWaAccountDO account = whatsAppService.getAccountInternal(conv.getAccountId());
        if (account == null) {
            return;
        }
        List<LlmClient.Message> history = waHistory(conversationId);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(history), history);
        String reason = blockReason(cfg.getWaMode(), g, conv.getCustomerId(),
                humanRepliedRecently(recent.stream()
                        .filter(t -> t.getDirection() == ChannelWaMessageDO.DIRECTION_OUT && !Boolean.TRUE.equals(t.getAiGenerated()))
                        .map(ChannelWaMessageDO::getMessageTime).toList(), cfg));
        if (reason == null && !whatsAppService.isWindowOpen(conv)) {
            reason = ChannelAiDraftDO.REASON_WINDOW_CLOSED;
        }
        if (reason != null) {
            saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING, reason, g.forbidden());
            return;
        }
        ChannelWaSendReqVO req = new ChannelWaSendReqVO();
        req.setConversationId(conversationId);
        req.setText(g.content());
        try {
            whatsAppService.send(account.getUserId(), req, true);
            saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_SENT, null, null);
        } catch (Exception e) {
            saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING,
                    ChannelAiDraftDO.REASON_SEND_FAILED, e.getMessage());
        }
    }

    /** 不能自动发出的原因；返回 null 表示可以发 */
    private String blockReason(String mode, Generated g, Long customerId, boolean humanActive) {
        if (g.handoff()) {
            return ChannelAiDraftDO.REASON_HANDOFF;
        }
        if (g.forbidden() != null) {
            return ChannelAiDraftDO.REASON_FORBIDDEN;
        }
        if (ChannelAiConfigDO.MODE_DRAFT.equals(mode)) {
            return ChannelAiDraftDO.REASON_DRAFT_MODE;
        }
        if (humanActive) {
            return ChannelAiDraftDO.REASON_HUMAN_ACTIVE;
        }
        if (customerId != null) { // 已建档的客户需开启「AI 托管」才自动回复；新询盘默认托管
            CrmCustomerDO c = customerMapper.selectById(customerId);
            if (c != null && !Boolean.TRUE.equals(c.getAiHosted())) {
                return ChannelAiDraftDO.REASON_NOT_HOSTED;
            }
        }
        return null;
    }

    private static boolean humanRepliedRecently(List<LocalDateTime> humanTimes, ChannelAiConfigDO cfg) {
        int hours = cfg.getHumanPauseHours() == null ? 12 : cfg.getHumanPauseHours();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return hours > 0 && humanTimes.stream().anyMatch(t -> t != null && t.isAfter(since));
    }

}
