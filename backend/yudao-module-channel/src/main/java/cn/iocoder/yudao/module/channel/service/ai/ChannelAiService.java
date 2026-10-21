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
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelAiDeferredDO;
import cn.iocoder.yudao.module.channel.dal.mysql.worktime.ChannelAiDeferredMapper;
import cn.iocoder.yudao.module.channel.service.worktime.ChannelWorkTimeService;
import cn.iocoder.yudao.module.channel.service.worktime.ChannelWorkTimeService.Decision;
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
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private ChannelWorkTimeService channelWorkTimeService;
    @Resource
    private ChannelAiDeferredMapper channelAiDeferredMapper;

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
        k.setEmbedding("");
        k.setEmbeddingModel(""); // 内容可能变了，旧向量作废；下面能生成就立即生成
        if (k.getId() == null) {
            channelAiKnowledgeMapper.insert(k);
        } else {
            if (channelAiKnowledgeMapper.selectById(k.getId()) == null) {
                throw exception(AI_KNOWLEDGE_NOT_EXISTS);
            }
            channelAiKnowledgeMapper.updateById(k);
        }
        ChannelAiConfigDO cfg = getConfig();
        if (semanticEnabled(cfg) && channelCreditService.hasBalance()) {
            try {
                embedKnowledge(cfg, List.of(k), currentUserId());
            } catch (Exception e) {
                log.warn("[saveKnowledge][条目({}) 生成向量失败，暂按关键词检索] {}", k.getId(), e.getMessage());
            }
        }
        return k.getId();
    }

    // ==================== 语义检索 ====================

    private static final int EMBED_BATCH = 16;

    private static boolean semanticEnabled(ChannelAiConfigDO cfg) {
        return StrUtil.isNotBlank(cfg.getEmbeddingModel()) && StrUtil.isNotBlank(cfg.getLlmBaseUrl())
                && StrUtil.isNotBlank(cfg.getLlmApiKey());
    }

    private static Long currentUserId() {
        try {
            return cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private static String embedText(ChannelAiKnowledgeDO k) {
        return StrUtil.maxLength(StrUtil.nullToEmpty(k.getTitle()) + "\n" + StrUtil.nullToEmpty(k.getContent()), 4000);
    }

    /** 为条目生成向量并保存，按 token 扣智点；返回处理的条数 */
    private int embedKnowledge(ChannelAiConfigDO cfg, List<ChannelAiKnowledgeDO> items, Long userId) {
        int tokens = 0;
        for (int i = 0; i < items.size(); i += EMBED_BATCH) {
            List<ChannelAiKnowledgeDO> batch = items.subList(i, Math.min(items.size(), i + EMBED_BATCH));
            LlmClient.Embeddings e = LlmClient.embed(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getEmbeddingModel(),
                    batch.stream().map(ChannelAiService::embedText).toList());
            for (int j = 0; j < batch.size(); j++) {
                ChannelAiKnowledgeDO k = batch.get(j);
                k.setEmbedding(VectorMath.toJson(e.vectors().get(j))).setEmbeddingModel(cfg.getEmbeddingModel());
                channelAiKnowledgeMapper.updateById(new ChannelAiKnowledgeDO().setId(k.getId())
                        .setEmbedding(k.getEmbedding()).setEmbeddingModel(k.getEmbeddingModel()));
            }
            tokens += e.tokens();
        }
        if (!items.isEmpty()) {
            channelCreditService.consume("knowledge", null, userId, Math.max(tokens, 1), "知识库向量化");
        }
        return items.size();
    }

    /** 为还没有向量（或向量模型已更换）的启用条目建索引，返回处理条数 */
    public int reindexKnowledge(Long userId) {
        ChannelAiConfigDO cfg = getConfig();
        if (!semanticEnabled(cfg)) {
            throw exception(AI_EMBEDDING_NOT_CONFIGURED);
        }
        requireCredit();
        List<ChannelAiKnowledgeDO> todo = channelAiKnowledgeMapper.selectEnabled().stream()
                .filter(k -> !cfg.getEmbeddingModel().equals(k.getEmbeddingModel()) || StrUtil.isBlank(k.getEmbedding()))
                .toList();
        try {
            return embedKnowledge(cfg, todo, userId);
        } catch (LlmClient.LlmException e) {
            throw exception(AI_LLM_FAIL, e.getMessage());
        }
    }

    /** 语义得分 + 消耗的 token；未配置向量模型或调用失败时返回 null（退回关键词检索） */
    private record Semantic(Map<Long, Double> scores, int tokens) {
    }

    private Semantic semanticScores(ChannelAiConfigDO cfg, List<ChannelAiKnowledgeDO> all, String text) {
        if (!semanticEnabled(cfg) || StrUtil.isBlank(text)) {
            return null;
        }
        List<ChannelAiKnowledgeDO> indexed = all.stream()
                .filter(k -> cfg.getEmbeddingModel().equals(k.getEmbeddingModel()) && StrUtil.isNotBlank(k.getEmbedding()))
                .toList();
        if (indexed.isEmpty()) {
            return null;
        }
        try {
            LlmClient.Embeddings e = LlmClient.embed(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getEmbeddingModel(),
                    List.of(StrUtil.maxLength(text, 4000)));
            float[] q = e.vectors().get(0);
            Map<Long, Double> scores = new HashMap<>();
            for (ChannelAiKnowledgeDO k : indexed) {
                scores.put(k.getId(), VectorMath.cosine(q, VectorMath.parse(k.getEmbedding())));
            }
            return new Semantic(scores, e.tokens());
        } catch (Exception ex) {
            log.warn("[semanticScores][向量检索失败，退回关键词检索] {}", ex.getMessage());
            return null;
        }
    }

    /** 检索测试：返回命中的条目及得分（知识库页面「检索测试」） */
    public List<Map<String, Object>> searchKnowledge(Long userId, String q) {
        ChannelAiConfigDO cfg = getConfig();
        List<ChannelAiKnowledgeDO> all = channelAiKnowledgeMapper.selectEnabled();
        Semantic sem = semanticEnabled(cfg) && channelCreditService.hasBalance() ? semanticScores(cfg, all, q) : null;
        if (sem != null) {
            channelCreditService.consume("knowledge", null, userId, Math.max(sem.tokens(), 1), "知识库检索测试");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiPromptBuilder.Hit h : AiPromptBuilder.rank(all, q, sem == null ? null : sem.scores())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.knowledge().getId());
            m.put("type", h.knowledge().getType());
            m.put("title", h.knowledge().getTitle());
            m.put("content", StrUtil.maxLength(h.knowledge().getContent(), 200));
            m.put("keywordScore", h.keywordScore());
            m.put("semanticScore", Math.round(h.semanticScore() * 1000) / 1000.0);
            m.put("score", Math.round(h.score() * 100) / 100.0);
            m.put("semantic", sem != null);
            out.add(m);
            if (out.size() >= 10) {
                break;
            }
        }
        return out;
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
    public record Generated(String content, boolean handoff, String forbidden, int promptTokens, int completionTokens, String model,
                            int knowledgeHits, String question) {
    }

    private Generated generate(ChannelAiConfigDO cfg, String channel, String customerText, List<LlmClient.Message> history) {
        List<ChannelAiKnowledgeDO> knowledge = channelAiKnowledgeMapper.selectEnabled();
        Semantic sem = semanticScores(cfg, knowledge, customerText);
        String system = AiPromptBuilder.systemPrompt(cfg, knowledge, customerText, channel, sem == null ? null : sem.scores());
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
        int embedTokens = sem == null ? 0 : sem.tokens(); // 检索用的向量 token 一并计入本次消耗
        int hits = AiPromptBuilder.rank(knowledge, customerText, sem == null ? null : sem.scores()).size();
        return new Generated(content, handoff, hit, r.promptTokens() + embedTokens, r.completionTokens(), cfg.getLlmModel(),
                hits, StrUtil.maxLength(StrUtil.trim(customerText), 250));
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
        requireCredit();
        ChannelAiConfigDO cfg = getConfig();
        List<LlmClient.Message> history = mailHistory(m);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_MAIL, lastUserText(history), history);
        return charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), withSignature(g, cfg), g, ChannelAiDraftDO.STATUS_PENDING,
                g.handoff() ? ChannelAiDraftDO.REASON_HANDOFF : g.forbidden() != null ? ChannelAiDraftDO.REASON_FORBIDDEN : ChannelAiDraftDO.REASON_MANUAL,
                g.forbidden()), userId, "邮件起草");
    }

    public ChannelAiDraftDO draftForWhatsApp(Long userId, Long conversationId) {
        whatsAppService.getConversation(userId, conversationId); // 校验归属
        requireCredit();
        ChannelAiConfigDO cfg = getConfig();
        List<LlmClient.Message> history = waHistory(conversationId);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(history), history);
        return charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING,
                g.handoff() ? ChannelAiDraftDO.REASON_HANDOFF : g.forbidden() != null ? ChannelAiDraftDO.REASON_FORBIDDEN : ChannelAiDraftDO.REASON_MANUAL,
                g.forbidden()), userId, "WhatsApp 起草");
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
                .promptTokens(g.promptTokens()).completionTokens(g.completionTokens()).model(g.model())
                .knowledgeHits(g.knowledgeHits()).question(g.question()).build();
        channelAiDraftMapper.insert(d);
        return d;
    }

    private void requireCredit() {
        if (!channelCreditService.hasBalance()) {
            throw exception(AI_CREDIT_INSUFFICIENT);
        }
    }

    /** 按本次 token 扣点，并记在回复记录上 */
    private ChannelAiDraftDO charge(ChannelAiDraftDO d, Long userId, String remark) {
        int tokens = (d.getPromptTokens() == null ? 0 : d.getPromptTokens()) + (d.getCompletionTokens() == null ? 0 : d.getCompletionTokens());
        long credits = channelCreditService.consume(d.getChannel(), d.getId(), userId, tokens, remark);
        channelAiDraftMapper.updateById(new ChannelAiDraftDO().setId(d.getId()).setCredits(credits));
        d.setCredits(credits);
        return d;
    }

    /** 余额不足时不调用大模型，只留一条记录提醒人工处理 */
    private boolean noCredit(String channel, Long refId) {
        if (channelCreditService.hasBalance()) {
            return false;
        }
        ChannelAiDraftDO exist = channelAiDraftMapper.selectLatestPending(channel, refId);
        if (exist == null || !ChannelAiDraftDO.REASON_NO_CREDIT.equals(exist.getReason())) {
            channelAiDraftMapper.insert(ChannelAiDraftDO.builder().channel(channel).refId(refId).content("")
                    .status(ChannelAiDraftDO.STATUS_PENDING).reason(ChannelAiDraftDO.REASON_NO_CREDIT).build());
        }
        return true;
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
        handleMailInbound(messageId, false);
    }

    /** @param afterWait 是否为「业务员优先」等待到期后的处理（不再重新等待） */
    public void handleMailInbound(Long messageId, boolean afterWait) {
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
        Decision wt = workTime(cfg.getMailMode(), afterWait);
        if (Decision.DEFER.equals(wt.action())) {
            defer(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), m.getId(), wt.deferSeconds());
            return;
        }
        if (noCredit(ChannelAiDraftDO.CHANNEL_MAIL, m.getId())) {
            return;
        }
        List<LlmClient.Message> history = mailHistory(m);
        Generated g = generate(withWorkTime(cfg, wt), ChannelAiDraftDO.CHANNEL_MAIL, lastUserText(history), history);
        String content = withSignature(g, cfg);
        String reason = blockReason(cfg.getMailMode(), g, m.getCustomerId(),
                humanRepliedRecently(mailService.getThreadInternal(m).stream()
                        .filter(t -> t.getDirection() == ChannelMailMessageDO.DIRECTION_OUT && !Boolean.TRUE.equals(t.getAiGenerated()))
                        .map(ChannelMailMessageDO::getSentTime).toList(), cfg));
        if (reason == null && Decision.DRAFT.equals(wt.action())) {
            reason = wt.reason();
        }
        if (reason != null) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), content, g, ChannelAiDraftDO.STATUS_PENDING, reason, g.forbidden()), account.getUserId(), "邮件自动处理");
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
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), content, g, ChannelAiDraftDO.STATUS_SENT, null, null), account.getUserId(), "邮件自动处理");
        } catch (Exception e) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), content, g, ChannelAiDraftDO.STATUS_PENDING,
                    ChannelAiDraftDO.REASON_SEND_FAILED, e.getMessage()), account.getUserId(), "邮件自动处理");
        }
    }

    /**
     * @param messageId 触发本次处理的消息；如果之后客户又发了新消息，本次放弃，交给最后一条处理（合并连发）
     */
    public void handleWhatsAppInbound(Long conversationId, Long messageId) {
        handleWhatsAppInbound(conversationId, messageId, false);
    }

    public void handleWhatsAppInbound(Long conversationId, Long messageId, boolean afterWait) {
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
        Decision wt = workTime(cfg.getWaMode(), afterWait);
        if (Decision.DEFER.equals(wt.action())) {
            defer(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, messageId, wt.deferSeconds());
            return;
        }
        if (noCredit(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId)) {
            return;
        }
        List<LlmClient.Message> history = waHistory(conversationId);
        Generated g = generate(withWorkTime(cfg, wt), ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(history), history);
        String reason = blockReason(cfg.getWaMode(), g, conv.getCustomerId(),
                humanRepliedRecently(recent.stream()
                        .filter(t -> t.getDirection() == ChannelWaMessageDO.DIRECTION_OUT && !Boolean.TRUE.equals(t.getAiGenerated()))
                        .map(ChannelWaMessageDO::getMessageTime).toList(), cfg));
        if (reason == null && Decision.DRAFT.equals(wt.action())) {
            reason = wt.reason();
        }
        if (reason == null && !whatsAppService.isWindowOpen(conv)) {
            reason = ChannelAiDraftDO.REASON_WINDOW_CLOSED;
        }
        if (reason != null) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING, reason, g.forbidden()), account.getUserId(), "WhatsApp 自动处理");
            return;
        }
        ChannelWaSendReqVO req = new ChannelWaSendReqVO();
        req.setConversationId(conversationId);
        req.setText(g.content());
        try {
            whatsAppService.send(account.getUserId(), req, true);
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_SENT, null, null), account.getUserId(), "WhatsApp 自动处理");
        } catch (Exception e) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING,
                    ChannelAiDraftDO.REASON_SEND_FAILED, e.getMessage()), account.getUserId(), "WhatsApp 自动处理");
        }
    }

    // ==================== 工作时间 ====================

    /** 只有「自动回复」模式受工作时间影响；等待到期后的处理不再重新等待 */
    private Decision workTime(String mode, boolean afterWait) {
        if (!ChannelAiConfigDO.MODE_AUTO.equals(mode)) {
            return new Decision(Decision.PROCESS, false, null, 0, null, null);
        }
        Decision d = channelWorkTimeService.decide();
        if (afterWait && Decision.DEFER.equals(d.action())) {
            return new Decision(Decision.PROCESS, false, null, 0, d.localTime(), null);
        }
        return d;
    }

    /** 非工作时间：提示 AI 说明业务员会在上班后跟进 */
    private static ChannelAiConfigDO withWorkTime(ChannelAiConfigDO cfg, Decision wt) {
        if (!wt.offHours()) {
            return cfg;
        }
        ChannelAiConfigDO copy = cn.iocoder.yudao.framework.common.util.object.BeanUtils.toBean(cfg, ChannelAiConfigDO.class);
        String note = "现在是公司的非工作时间（当地时间 " + wt.localTime() + "）。能根据资料回答的照常回答；"
                + "需要业务员处理的事项，告诉客户业务员会在上班后" + (wt.nextWorkTime() == null ? "" : "（约 " + wt.nextWorkTime() + "）")
                + "尽快跟进。";
        copy.setExtraInstructions(StrUtil.isBlank(cfg.getExtraInstructions()) ? note : cfg.getExtraInstructions().trim() + "\n" + note);
        return copy;
    }

    /** 业务员优先：记下等待项，到期仍无人回复再由 AI 处理；同一对象之前的等待项作废 */
    private void defer(String channel, Long refId, Long messageId, long seconds) {
        channelAiDeferredMapper.supersede(channel, refId);
        channelAiDeferredMapper.insert(ChannelAiDeferredDO.builder().channel(channel).refId(refId).messageId(messageId)
                .dueTime(LocalDateTime.now().plusSeconds(seconds)).status(ChannelAiDeferredDO.STATUS_WAITING).build());
    }

    /** 等待到期：已有人回复则放弃；客户又发了新消息则交给新消息；否则由 AI 处理。返回最终状态 */
    public String resumeDeferred(ChannelAiDeferredDO d) {
        if (ChannelAiDraftDO.CHANNEL_MAIL.equals(d.getChannel())) {
            ChannelMailMessageDO m = mailService.getMessageInternal(d.getRefId());
            if (m == null) {
                return ChannelAiDeferredDO.STATUS_SUPERSEDED;
            }
            boolean replied = mailService.getThreadInternal(m).stream()
                    .anyMatch(t -> t.getDirection() == ChannelMailMessageDO.DIRECTION_OUT && t.getId() > m.getId());
            if (replied) {
                return ChannelAiDeferredDO.STATUS_HUMAN_REPLIED;
            }
            handleMailInbound(m.getId(), true);
            return ChannelAiDeferredDO.STATUS_AI_HANDLED;
        }
        List<ChannelWaMessageDO> recent = whatsAppService.getRecentMessagesInternal(d.getRefId(), WA_HISTORY);
        Optional<ChannelWaMessageDO> lastIn = recent.stream().filter(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_IN)
                .reduce((a, b) -> b);
        if (lastIn.isEmpty() || !lastIn.get().getId().equals(d.getMessageId())) {
            return ChannelAiDeferredDO.STATUS_SUPERSEDED;
        }
        if (recent.get(recent.size() - 1).getDirection() == ChannelWaMessageDO.DIRECTION_OUT) {
            return ChannelAiDeferredDO.STATUS_HUMAN_REPLIED;
        }
        handleWhatsAppInbound(d.getRefId(), d.getMessageId(), true);
        return ChannelAiDeferredDO.STATUS_AI_HANDLED;
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
