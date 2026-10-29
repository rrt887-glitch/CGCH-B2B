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
import cn.iocoder.yudao.module.channel.dal.dataobject.cloud.ChannelCloudFileDO;
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
    private cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService channelNotifyService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.cases.ChannelAiCaseService channelAiCaseService;
    @Resource
    private ChannelAiDeferredMapper channelAiDeferredMapper;
    @Resource
    private cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper channelWaMessageMapper;

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

    /** 公司物料：管理员维护 */
    public Long saveKnowledge(ChannelAiKnowledgeDO k) {
        if (k.getId() != null) {
            ChannelAiKnowledgeDO old = channelAiKnowledgeMapper.selectById(k.getId());
            if (old == null || old.isPersonal()) {
                throw exception(AI_KNOWLEDGE_NOT_EXISTS);
            }
        }
        k.setScope(ChannelAiKnowledgeDO.SCOPE_ENTERPRISE);
        k.setOwnerUserId(null);
        return doSaveKnowledge(k);
    }

    // ==================== 个人物料：业务员自己维护，只在他负责的会话里使用 ====================

    public List<ChannelAiKnowledgeDO> listPersonal(Long userId) {
        return channelAiKnowledgeMapper.selectPersonal(userId);
    }

    public Long savePersonal(Long userId, ChannelAiKnowledgeDO k) {
        if (k.getId() != null) {
            validatePersonal(userId, k.getId());
        }
        if (!ChannelAiKnowledgeDO.TYPE_FAQ.equals(k.getType())) {
            k.setType(ChannelAiKnowledgeDO.TYPE_PRODUCT); // 个人物料不能写企业介绍（企业介绍每次回复都会带上）
        }
        k.setScope(ChannelAiKnowledgeDO.SCOPE_PERSONAL);
        k.setOwnerUserId(userId);
        k.setFileId(null);
        return doSaveKnowledge(k);
    }

    public void deletePersonal(Long userId, Long id) {
        validatePersonal(userId, id);
        channelAiKnowledgeMapper.deleteById(id);
    }

    private void validatePersonal(Long userId, Long id) {
        ChannelAiKnowledgeDO old = channelAiKnowledgeMapper.selectById(id);
        if (old == null || !old.isPersonal() || !java.util.Objects.equals(old.getOwnerUserId(), userId)) {
            throw exception(AI_KNOWLEDGE_NOT_EXISTS);
        }
    }

    private Long doSaveKnowledge(ChannelAiKnowledgeDO k) {
        if (k.getCategory() != null && !ChannelAiKnowledgeDO.CATEGORIES.contains(k.getCategory())) {
            k.setCategory(null);
        }
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

    /** 配置了向量模型且有余额时为这些条目生成向量；失败只记日志（之后可在知识库里「建立语义索引」补上） */
    public void embedQuietly(List<ChannelAiKnowledgeDO> items) {
        ChannelAiConfigDO cfg = getConfig();
        if (items.isEmpty() || !semanticEnabled(cfg) || !channelCreditService.hasBalance()) {
            return;
        }
        try {
            embedKnowledge(cfg, items, currentUserId());
        } catch (Exception e) {
            log.warn("[embedQuietly][{} 条生成向量失败，暂按关键词检索] {}", items.size(), e.getMessage());
        }
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
    private record Semantic(Map<Long, Double> scores, int tokens, float[] query) {
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
            return new Semantic(scores, e.tokens(), q);
        } catch (Exception ex) {
            log.warn("[semanticScores][向量检索失败，退回关键词检索] {}", ex.getMessage());
            return null;
        }
    }

    /** 检索测试：返回命中的条目及得分（知识库页面「检索测试」） */
    public List<Map<String, Object>> searchKnowledge(Long userId, String q) {
        ChannelAiConfigDO cfg = getConfig();
        List<ChannelAiKnowledgeDO> all = channelAiKnowledgeMapper.selectEnabledFor(userId);
        Semantic sem = semanticEnabled(cfg) && channelCreditService.hasBalance() ? semanticScores(cfg, all, q) : null;
        if (sem != null) {
            channelCreditService.consume("knowledge", null, userId, Math.max(sem.tokens(), 1), "知识库检索测试");
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (AiPromptBuilder.Hit h : AiPromptBuilder.rank(all, q, sem == null ? null : sem.scores())) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", h.knowledge().getId());
            m.put("type", h.knowledge().getType());
            m.put("scope", h.knowledge().isPersonal() ? ChannelAiKnowledgeDO.SCOPE_PERSONAL : ChannelAiKnowledgeDO.SCOPE_ENTERPRISE);
            m.put("category", h.knowledge().getCategory());
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
        ChannelAiKnowledgeDO old = channelAiKnowledgeMapper.selectById(id);
        if (old == null || old.isPersonal()) {
            throw exception(AI_KNOWLEDGE_NOT_EXISTS);
        }
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
    /**
     * @param intent      咨询意图（模型在回复第一行给出的分类），可能为空
     * @param supportType 转人工时的看板分类（handoff、missing、permission、crisis、other）
     */
    /** files：AI 决定一并发送的企业云盘文件 */
    public record Generated(String content, boolean handoff, String forbidden, int promptTokens, int completionTokens, String model,
                            int knowledgeHits, String question, String intent, String supportType, List<ChannelCloudFileDO> files) {
    }

    /** 回复里的发送文件标记 */
    private static final Pattern SEND_FILE = Pattern.compile("\\[SEND_FILE:\\s*(\\d+)\\s*]");

    @Resource
    private cn.iocoder.yudao.module.channel.service.cloud.ChannelCloudFileService channelCloudFileService;

    /** 邮件：AI 选的云盘文件转成附件编号 */
    private List<Long> mailAttachments(Generated g, Long userId) {
        List<Long> ids = new ArrayList<>();
        for (ChannelCloudFileDO f : g.files()) {
            ids.add(mailService.attachStoredFile(userId, f.getName(), f.getMimeType(), f.getSize(), f.getUrl()));
        }
        return ids;
    }

    /** WhatsApp / Messenger：文字发出后逐个发送 AI 选的文件；单个失败只记日志 */
    private void sendWaFiles(Generated g, Long userId, Long conversationId) {
        for (ChannelCloudFileDO f : g.files()) {
            try {
                byte[] bytes = channelCloudFileService.content(f);
                var m = whatsAppService.sendMedia(userId, conversationId,
                        new cn.iocoder.yudao.module.channel.service.file.BytesMultipartFile(f.getName(), f.getMimeType(), bytes), null);
                if (m != null && m.getId() != null) {
                    channelWaMessageMapper.updateById(new ChannelWaMessageDO().setId(m.getId()).setAiGenerated(true));
                }
            } catch (Exception e) {
                log.warn("[sendWaFiles][会话 {} 发送文件 {} 失败] {}", conversationId, f.getName(), e.getMessage());
            }
        }
    }

    /** 没有自动发出（起草、转人工）时，把 AI 建议的附件写在草稿末尾，方便业务员一起发 */
    private static String withFileNote(String content, Generated g) {
        if (g.files().isEmpty()) {
            return content;
        }
        return StrUtil.nullToEmpty(content) + "\n\n（AI 建议附上云盘文件：" + g.files().stream().map(ChannelCloudFileDO::getName)
                .collect(java.util.stream.Collectors.joining("、")) + "）";
    }

    /**
     * 允许联网报价：客户在问价格、企业资料里又没有价格时，联网检索公开市场参考价附在提示里。
     * AI 只能把它当作市场参考区间说明，并注明以正式报价为准。
     */
    private String webQuoteBlock(ChannelAiConfigDO cfg, String customerText, String system) {
        ChannelAgentSettings.Skills skills = ChannelAgentSettings.skills(cfg);
        if (!skills.isQuote() || !skills.isWebQuote() || StrUtil.isBlank(cfg.getSearchApiKey())
                || !WebSearchClient.asksPrice(customerText)) {
            return "";
        }
        int i = system.lastIndexOf("## 企业资料");
        if (WebSearchClient.hasPrice(i < 0 ? system : system.substring(i))) {
            return ""; // 企业资料里有价格，以资料为准
        }
        String company = ChannelAgentSettings.company(cfg).getIndustry();
        String query = StrUtil.maxLength(StrUtil.trim(customerText).replaceAll("\\s+", " "), 300) + " wholesale price per unit"
                + (StrUtil.isBlank(company) ? "" : " " + company);
        List<WebSearchClient.Hit> hits = WebSearchClient.search(cfg.getSearchBaseUrl(), cfg.getSearchApiKey(), query);
        if (hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n## 公开市场参考价（联网检索，仅供参考）\n")
                .append("企业资料里没有这次询问的价格。可以参考下列公开信息，给客户一个大致的市场价格区间，")
                .append("必须说明这是市场参考价、最终价格以我们的正式报价为准，并询问数量、规格等信息以便正式报价；")
                .append("不要编造下列信息里没有的数字，不要提及信息来源网站。\n");
        for (WebSearchClient.Hit h : hits) {
            sb.append("- ").append(h.title()).append("：").append(h.content()).append("\n");
        }
        return sb.toString();
    }

    /** 配技能「AI 发送文件」开启时，列出可以发送的文件，告诉 AI 怎么标记 */
    private String fileBlock(ChannelAiConfigDO cfg, List<ChannelCloudFileDO> files) {
        if (files.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n## 可以发给客户的资料文件\n")
                .append("客户明确需要或明显会用到下列资料（报价单、产品目录、参数表、证书等）时，在回复正文的最后单独一行写 [SEND_FILE:编号]，")
                .append("系统会把文件作为附件发出。一次最多 3 个，只发和客户需求直接相关的；正文里可以说「附件是……」，不要写文件链接；")
                .append("没有合适的文件就不要写标记。\n");
        for (ChannelCloudFileDO f : files) {
            sb.append("- 编号 ").append(f.getId()).append("：").append(f.getName());
            if (StrUtil.isNotBlank(f.getDescription())) {
                sb.append("（").append(f.getDescription()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 生成回复。handlerUserId 是这次回复代表的业务员（会话的处理人 / 邮箱所属人 / 点「AI 起草」的人），
     * 决定能用到哪些个人物料；公司物料所有人都能用。
     */
    private Generated generate(ChannelAiConfigDO cfg, String channel, String customerText, List<LlmClient.Message> history, Long handlerUserId) {
        List<ChannelAiKnowledgeDO> knowledge = channelAiKnowledgeMapper.selectEnabledFor(handlerUserId);
        Semantic sem = semanticScores(cfg, knowledge, customerText);
        List<ChannelCloudFileDO> sendable = ChannelAgentSettings.skills(cfg).isSendFiles() ? channelCloudFileService.aiSendable() : List.of();
        String system = AiPromptBuilder.systemPrompt(cfg, knowledge, customerText, channel, sem == null ? null : sem.scores());
        system = system + webQuoteBlock(cfg, customerText, system)
                + channelAiCaseService.promptBlock(customerText, sem == null ? null : sem.query()) + fileBlock(cfg, sendable);
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
        String intent = AiPromptBuilder.intent(r.content());
        String supportType = handoff ? AiPromptBuilder.handoffType(r.content()) : null;
        String content = handoff ? "" : AiPromptBuilder.stripIntent(r.content());
        List<ChannelCloudFileDO> files = new ArrayList<>();
        java.util.regex.Matcher fm = SEND_FILE.matcher(content);
        while (fm.find() && files.size() < 3) {
            long id = Long.parseLong(fm.group(1));
            sendable.stream().filter(f -> f.getId() == id && !files.contains(f)).findFirst().ifPresent(files::add);
        }
        content = SEND_FILE.matcher(content).replaceAll("").replaceAll("\\n{3,}", "\n\n").trim();
        String hit = handoff ? null : AiPromptBuilder.firstForbidden(content,
                channelAiForbiddenWordMapper.selectEnabled().stream().map(ChannelAiForbiddenWordDO::getWord).toList());
        int embedTokens = sem == null ? 0 : sem.tokens(); // 检索用的向量 token 一并计入本次消耗
        int hits = AiPromptBuilder.rank(knowledge, customerText, sem == null ? null : sem.scores()).size();
        return new Generated(content, handoff, hit, r.promptTokens() + embedTokens, r.completionTokens(), cfg.getLlmModel(),
                hits, StrUtil.maxLength(StrUtil.trim(customerText), 250), intent, supportType, files);
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

    // ==================== 智能体测试对话 ====================

    /**
     * 测试数字员工：用当前的人设、知识库、话术规则回复一段模拟的客户对话，不保存记录，按 token 扣点。
     *
     * @param channel  mail 或 whatsapp（会话），影响回复格式
     * @param messages 对话历史，role 为 user（客户）或 assistant（数字员工）
     */
    public Map<String, Object> testReply(Long userId, String channel, List<LlmClient.Message> messages) {
        requireCredit();
        ChannelAiConfigDO cfg = getConfig();
        String ch = ChannelAiDraftDO.CHANNEL_MAIL.equals(channel) ? ChannelAiDraftDO.CHANNEL_MAIL : ChannelAiDraftDO.CHANNEL_WHATSAPP;
        List<LlmClient.Message> history = messages.stream()
                .filter(m -> StrUtil.isNotBlank(m.content()) && ("user".equals(m.role()) || "assistant".equals(m.role())))
                .map(m -> new LlmClient.Message(m.role(), StrUtil.maxLength(m.content(), HISTORY_CHARS))).toList();
        if (history.isEmpty() || !"user".equals(history.get(history.size() - 1).role())) {
            throw exception(AI_LLM_FAIL, "请先输入客户的问题");
        }
        Generated g = generate(cfg, ch, lastUserText(history), history.subList(Math.max(0, history.size() - 20), history.size()), userId);
        long credits = channelCreditService.consume(ch, 0L, userId, g.promptTokens() + g.completionTokens(), "智能体测试对话");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("content", ChannelAiDraftDO.CHANNEL_MAIL.equals(ch) ? withSignature(g, cfg) : g.content());
        r.put("handoff", g.handoff());
        r.put("forbidden", g.forbidden());
        r.put("knowledgeHits", g.knowledgeHits());
        r.put("files", g.files().stream().map(ChannelCloudFileDO::getName).toList());
        r.put("credits", credits);
        return r;
    }

    // ==================== 手动起草（工作台「AI 起草」按钮） ====================

    public ChannelAiDraftDO draftForMail(Long userId, Long messageId) {
        ChannelMailMessageDO m = mailService.getMessage(userId, messageId);
        requireCredit();
        ChannelAiConfigDO cfg = getConfig();
        List<LlmClient.Message> history = mailHistory(m);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_MAIL, lastUserText(history), history, userId);
        return charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), withFileNote(withSignature(g, cfg), g), g, ChannelAiDraftDO.STATUS_PENDING,
                g.handoff() ? ChannelAiDraftDO.REASON_HANDOFF : g.forbidden() != null ? ChannelAiDraftDO.REASON_FORBIDDEN : ChannelAiDraftDO.REASON_MANUAL,
                g.forbidden()), userId, "邮件起草");
    }

    public ChannelAiDraftDO draftForWhatsApp(Long userId, Long conversationId) {
        whatsAppService.getConversation(userId, conversationId); // 校验归属
        requireCredit();
        ChannelAiConfigDO cfg = getConfig();
        List<LlmClient.Message> history = waHistory(conversationId);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(history), history, userId);
        return charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, withFileNote(g.content(), g), g, ChannelAiDraftDO.STATUS_PENDING,
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
                .knowledgeHits(g.knowledgeHits()).question(g.question())
                .supportType(g.handoff() ? g.supportType() : g.forbidden() != null ? "compliance"
                        : ChannelAiDraftDO.REASON_CONDITION.equals(reason) ? "handoff" : null).build();
        channelAiDraftMapper.insert(d);
        saveIntent(channel, refId, g.intent());
        return d;
    }

    /** 记录咨询意图（只在尚未分类时写入），看板「流量占比」据此统计 */
    private void saveIntent(String channel, Long refId, String intent) {
        if (intent == null || refId == null) {
            return;
        }
        try {
            if (ChannelAiDraftDO.CHANNEL_MAIL.equals(channel)) {
                ChannelMailMessageDO m = mailService.getMessageInternal(refId);
                if (m != null && m.getIntent() == null) {
                    mailService.updateIntentInternal(m.getId(), intent);
                }
            } else {
                ChannelWaConversationDO c = whatsAppService.getConversationInternal(refId);
                if (c != null && c.getIntent() == null) {
                    whatsAppService.updateIntentInternal(c.getId(), intent);
                }
            }
        } catch (Exception e) {
            log.warn("[saveIntent][{} {}] {}", channel, refId, e.getMessage());
        }
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
        if (!ChannelAiDraftDO.REASON_MANUAL.equals(d.getReason())) {
            channelNotifyService.onDraft(d); // 转人工、违禁词、发送失败时提醒业务员
        }
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
        Generated g = generate(withWorkTime(cfg, wt), ChannelAiDraftDO.CHANNEL_MAIL, lastUserText(history), history, account.getUserId());
        String content = withSignature(g, cfg);
        String reason = blockReason(cfg.getMailMode(), g, m.getCustomerId(),
                humanRepliedRecently(mailService.getThreadInternal(m).stream()
                        .filter(t -> t.getDirection() == ChannelMailMessageDO.DIRECTION_OUT && !Boolean.TRUE.equals(t.getAiGenerated()))
                        .map(ChannelMailMessageDO::getSentTime).toList(), cfg));
        if (reason == null && Decision.DRAFT.equals(wt.action())) {
            reason = wt.reason();
        }
        if (reason != null) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, m.getId(), withFileNote(content, g), g, ChannelAiDraftDO.STATUS_PENDING, reason, g.forbidden()), account.getUserId(), "邮件自动处理");
            return;
        }
        ChannelMailSendReqVO req = new ChannelMailSendReqVO();
        req.setAccountId(account.getId());
        req.setTo(List.of(m.getFromAddress()));
        req.setSubject(StrUtil.startWithIgnoreCase(m.getSubject(), "re:") ? m.getSubject() : "Re: " + StrUtil.nullToEmpty(m.getSubject()));
        req.setText(content);
        req.setReplyToId(m.getId());
        try {
            req.setAttachmentIds(mailAttachments(g, account.getUserId()));
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
        ChannelAgentSettings.Opening opening = ChannelAgentSettings.opening(cfg);
        boolean auto = ChannelAiConfigDO.MODE_AUTO.equals(cfg.getWaMode()) && !Decision.DRAFT.equals(wt.action());
        boolean firstContact = recent.stream().noneMatch(x -> x.getDirection() == ChannelWaMessageDO.DIRECTION_OUT);
        if (auto && firstContact && opening.isEnabled() && StrUtil.isNotBlank(opening.getGreeting()) && whatsAppService.isWindowOpen(conv)) {
            sendQuietly(account.getUserId(), conversationId, opening.getGreeting().trim()); // 固定开场白：新客户先收到问候
            sendOpeningFiles(account.getUserId(), conversationId, opening.getFileIds());
        }
        List<LlmClient.Message> history = waHistory(conversationId);
        Generated g = generate(withWorkTime(cfg, wt), ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(history), history, conv.handlerUserId(account));
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
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, withFileNote(g.content(), g), g, ChannelAiDraftDO.STATUS_PENDING, reason, g.forbidden()), account.getUserId(), "WhatsApp 自动处理");
            if (auto && g.handoff() && StrUtil.isNotBlank(opening.getFallback()) && whatsAppService.isWindowOpen(conv)) {
                sendQuietly(account.getUserId(), conversationId, opening.getFallback().trim()); // 兜底话术：告诉客户稍后由同事回复
            }
            return;
        }
        ChannelWaSendReqVO req = new ChannelWaSendReqVO();
        req.setConversationId(conversationId);
        req.setText(g.content());
        try {
            whatsAppService.send(account.getUserId(), req, true);
            sendWaFiles(g, account.getUserId(), conversationId);
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_SENT, null, null), account.getUserId(), "WhatsApp 自动处理");
        } catch (Exception e) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_PENDING,
                    ChannelAiDraftDO.REASON_SEND_FAILED, e.getMessage()), account.getUserId(), "WhatsApp 自动处理");
        }
    }

    // ==================== 智能跟进 ====================

    /** 跟进最多回看多少天内 AI 发出的消息 */
    private static final int FOLLOW_UP_LOOKBACK_DAYS = 30;

    /**
     * 智能跟进（定时调用，已在租户上下文中）：AI 发出的消息之后客户一直没回复，到了间隔就由 AI 再跟进一次，最多跟进设定的轮数。
     * 只在「自动回复」模式、工作时间允许 AI 接待时执行；WhatsApp 只在 24 小时会话窗口内跟进。
     *
     * @return 本次发出的跟进条数
     */
    public int runFollowUps() {
        ChannelAiConfigDO cfg = channelAiConfigMapper.selectCurrent();
        if (cfg == null || StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
            return 0;
        }
        ChannelAgentSettings.Skills s = ChannelAgentSettings.skills(cfg);
        if (!s.isFollowUp() || !channelCreditService.hasBalance()) {
            return 0;
        }
        int hours = Math.max(1, s.getFollowUpHours());
        int rounds = Math.max(1, Math.min(3, s.getFollowUpRounds()));
        int sent = 0;
        if (ChannelAiConfigDO.MODE_AUTO.equals(cfg.getMailMode()) && Decision.PROCESS.equals(workTime(cfg.getMailMode(), false).action())) {
            sent += mailFollowUps(cfg, hours, rounds);
        }
        if (ChannelAiConfigDO.MODE_AUTO.equals(cfg.getWaMode()) && Decision.PROCESS.equals(workTime(cfg.getWaMode(), false).action())) {
            sent += waFollowUps(cfg, hours, rounds);
        }
        return sent;
    }

    private static String followUpPrompt(int round, long hoursSince, String channel) {
        String gap = hoursSince >= 48 ? (hoursSince / 24) + " 天" : hoursSince + " 小时";
        return "[系统提示，不是客户发的] 客户在你上一条" + ("mail".equals(channel) ? "邮件" : "消息") + "之后 " + gap + "没有回复。"
                + "请写第 " + round + " 次跟进：简短、礼貌、自然，不要重复上一条的内容；可以补充一条对客户有价值的信息，"
                + "或提一个容易回答的问题，推动客户回复。不要提到“系统提示”。";
    }

    private int mailFollowUps(ChannelAiConfigDO cfg, int hours, int rounds) {
        LocalDateTime now = LocalDateTime.now();
        List<ChannelMailMessageDO> candidates = channelMailMessageMapper.selectList(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ChannelMailMessageDO>()
                        .eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_OUT)
                        .eq(ChannelMailMessageDO::getAiGenerated, true)
                        .between(ChannelMailMessageDO::getSentTime, now.minusDays(FOLLOW_UP_LOOKBACK_DAYS), now.minusHours(hours))
                        .isNotNull(ChannelMailMessageDO::getThreadKey)
                        .orderByDesc(ChannelMailMessageDO::getId).last("LIMIT 200"));
        Set<String> seen = new HashSet<>();
        int sent = 0;
        for (ChannelMailMessageDO c : candidates) {
            if (!seen.add(c.getThreadKey())) {
                continue;
            }
            try {
                if (mailFollowUp(cfg, c, hours, rounds)) {
                    sent++;
                }
            } catch (Exception e) {
                log.warn("[mailFollowUps][邮件({}) 跟进失败] {}", c.getId(), e.getMessage());
            }
        }
        return sent;
    }

    private boolean mailFollowUp(ChannelAiConfigDO cfg, ChannelMailMessageDO c, int hours, int rounds) {
        List<ChannelMailMessageDO> thread = mailService.getThreadInternal(c);
        if (thread.isEmpty()) {
            return false;
        }
        ChannelMailMessageDO last = thread.get(thread.size() - 1);
        if (last.getDirection() != ChannelMailMessageDO.DIRECTION_OUT || !Boolean.TRUE.equals(last.getAiGenerated())
                || last.getSentTime() == null || last.getSentTime().isAfter(LocalDateTime.now().minusHours(hours))) {
            return false; // 客户已回复、人工回复过，或还没到跟进时间
        }
        int trailingAi = 0;
        ChannelMailMessageDO lastIn = null;
        for (int i = thread.size() - 1; i >= 0; i--) {
            ChannelMailMessageDO t = thread.get(i);
            if (t.getDirection() == ChannelMailMessageDO.DIRECTION_IN) {
                lastIn = t;
                break;
            }
            if (!Boolean.TRUE.equals(t.getAiGenerated())) {
                return false; // 中间有人工发出的邮件，交给人工
            }
            trailingAi++;
        }
        int round = trailingAi; // 第一封是 AI 的回复，之后每一封是一次跟进
        if (lastIn == null || round > rounds || NO_REPLY.matcher(StrUtil.nullToEmpty(lastIn.getFromAddress())).find()) {
            return false;
        }
        ChannelMailAccountDO account = mailService.getAccountInternal(lastIn.getAccountId());
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            return false;
        }
        List<LlmClient.Message> history = new ArrayList<>();
        for (ChannelMailMessageDO t : thread.subList(Math.max(0, thread.size() - MAIL_HISTORY), thread.size())) {
            String body = HtmlText.snippet(StrUtil.isNotBlank(t.getTextBody()) ? t.getTextBody() : HtmlText.toText(t.getHtmlBody()), null, HISTORY_CHARS);
            history.add(t.getDirection() == ChannelMailMessageDO.DIRECTION_IN
                    ? LlmClient.Message.user("主题：" + StrUtil.nullToEmpty(t.getSubject()) + "\n" + body) : LlmClient.Message.assistant(body));
        }
        long since = java.time.Duration.between(last.getSentTime(), LocalDateTime.now()).toHours();
        history.add(LlmClient.Message.user(followUpPrompt(round, since, ChannelAiDraftDO.CHANNEL_MAIL)));
        String customerText = HtmlText.snippet(lastIn.getTextBody(), lastIn.getHtmlBody(), HISTORY_CHARS);
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_MAIL, customerText, history, account.getUserId());
        String reason = blockReason(ChannelAiConfigDO.MODE_AUTO, g, lastIn.getCustomerId(), false);
        if (reason != null || StrUtil.isBlank(g.content())) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, lastIn.getId(), g.content(), g, ChannelAiDraftDO.STATUS_DISCARDED,
                    ChannelAiDraftDO.REASON_FOLLOW_UP, reason), account.getUserId(), "智能跟进（未发送）");
            return false;
        }
        String content = withSignature(g, cfg);
        ChannelMailSendReqVO req = new ChannelMailSendReqVO();
        req.setAccountId(account.getId());
        req.setTo(List.of(lastIn.getFromAddress()));
        req.setSubject(StrUtil.startWithIgnoreCase(lastIn.getSubject(), "re:") ? lastIn.getSubject() : "Re: " + StrUtil.nullToEmpty(lastIn.getSubject()));
        req.setText(content);
        req.setReplyToId(lastIn.getId());
        req.setAttachmentIds(mailAttachments(g, account.getUserId()));
        mailService.send(account.getUserId(), req, true);
        charge(saveDraft(ChannelAiDraftDO.CHANNEL_MAIL, lastIn.getId(), content, g, ChannelAiDraftDO.STATUS_SENT,
                ChannelAiDraftDO.REASON_FOLLOW_UP, null), account.getUserId(), "智能跟进");
        log.info("[mailFollowUp][邮件往来 {} 第 {} 次跟进已发送]", c.getThreadKey(), round);
        return true;
    }

    private int waFollowUps(ChannelAiConfigDO cfg, int hours, int rounds) {
        if (hours >= 24) {
            return 0; // WhatsApp 只能在客户最后一条消息后 24 小时内主动发送普通消息
        }
        LocalDateTime now = LocalDateTime.now();
        List<ChannelWaMessageDO> candidates = channelWaMessageMapper.selectList(
                new cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX<ChannelWaMessageDO>()
                        .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_OUT)
                        .eq(ChannelWaMessageDO::getAiGenerated, true)
                        .between(ChannelWaMessageDO::getMessageTime, now.minusHours(24), now.minusHours(hours))
                        .orderByDesc(ChannelWaMessageDO::getId).last("LIMIT 200"));
        Set<Long> seen = new HashSet<>();
        int sent = 0;
        for (ChannelWaMessageDO c : candidates) {
            if (!seen.add(c.getConversationId())) {
                continue;
            }
            try {
                if (waFollowUp(cfg, c.getConversationId(), hours, rounds)) {
                    sent++;
                }
            } catch (Exception e) {
                log.warn("[waFollowUps][会话({}) 跟进失败] {}", c.getConversationId(), e.getMessage());
            }
        }
        return sent;
    }

    private boolean waFollowUp(ChannelAiConfigDO cfg, Long conversationId, int hours, int rounds) {
        ChannelWaConversationDO conv = whatsAppService.getConversationInternal(conversationId);
        if (conv == null || !whatsAppService.isWindowOpen(conv)) {
            return false;
        }
        List<ChannelWaMessageDO> recent = whatsAppService.getRecentMessagesInternal(conversationId, WA_HISTORY);
        if (recent.isEmpty()) {
            return false;
        }
        ChannelWaMessageDO last = recent.get(recent.size() - 1);
        if (last.getDirection() != ChannelWaMessageDO.DIRECTION_OUT || !Boolean.TRUE.equals(last.getAiGenerated())
                || last.getMessageTime() == null || last.getMessageTime().isAfter(LocalDateTime.now().minusHours(hours))) {
            return false;
        }
        int trailingAi = 0;
        boolean hasIn = false;
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChannelWaMessageDO t = recent.get(i);
            if (t.getDirection() == ChannelWaMessageDO.DIRECTION_IN) {
                hasIn = true;
                break;
            }
            if (!Boolean.TRUE.equals(t.getAiGenerated())) {
                return false;
            }
            trailingAi++;
        }
        int round = trailingAi;
        if (!hasIn || round > rounds) {
            return false;
        }
        ChannelWaAccountDO account = whatsAppService.getAccountInternal(conv.getAccountId());
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            return false;
        }
        List<LlmClient.Message> history = waHistory(conversationId);
        long since = java.time.Duration.between(last.getMessageTime(), LocalDateTime.now()).toHours();
        history.add(LlmClient.Message.user(followUpPrompt(round, since, ChannelAiDraftDO.CHANNEL_WHATSAPP)));
        Generated g = generate(cfg, ChannelAiDraftDO.CHANNEL_WHATSAPP, lastUserText(waHistory(conversationId)), history, conv.handlerUserId(account));
        String reason = blockReason(ChannelAiConfigDO.MODE_AUTO, g, conv.getCustomerId(), false);
        if (reason != null || StrUtil.isBlank(g.content())) {
            charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_DISCARDED,
                    ChannelAiDraftDO.REASON_FOLLOW_UP, reason), account.getUserId(), "智能跟进（未发送）");
            return false;
        }
        ChannelWaSendReqVO req = new ChannelWaSendReqVO();
        req.setConversationId(conversationId);
        req.setText(g.content());
        whatsAppService.send(account.getUserId(), req, true);
        sendWaFiles(g, account.getUserId(), conversationId);
        charge(saveDraft(ChannelAiDraftDO.CHANNEL_WHATSAPP, conversationId, g.content(), g, ChannelAiDraftDO.STATUS_SENT,
                ChannelAiDraftDO.REASON_FOLLOW_UP, null), account.getUserId(), "智能跟进");
        log.info("[waFollowUp][会话 {} 第 {} 次跟进已发送]", conversationId, round);
        return true;
    }

    /** 固定开场白附带的企业云盘文件 */
    private void sendOpeningFiles(Long userId, Long conversationId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }
        List<ChannelCloudFileDO> files = channelCloudFileService.getEnterpriseFiles(fileIds.stream().limit(2).toList());
        sendWaFiles(new Generated("", false, null, 0, 0, null, 0, null, null, null, files), userId, conversationId);
    }

    /** 由 AI 发送一条固定文本（开场白、兜底话术）；失败只记日志 */
    private void sendQuietly(Long userId, Long conversationId, String text) {
        try {
            ChannelWaSendReqVO req = new ChannelWaSendReqVO();
            req.setConversationId(conversationId);
            req.setText(text);
            whatsAppService.send(userId, req, true);
        } catch (Exception e) {
            log.warn("[sendQuietly][会话 {}] {}", conversationId, e.getMessage());
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
        ChannelAgentSettings.Handoff h = ChannelAgentSettings.handoff(channelAiConfigMapper.selectCurrent());
        CrmCustomerDO c = customerId == null ? null : customerMapper.selectById(customerId);
        if (humanActive) {
            if ("off".equals(h.getTakeoverMode()) && c != null && Boolean.TRUE.equals(c.getAiHosted())) {
                // 人工介入后关闭该客户的 AI 托管，之后由人工跟进
                customerMapper.updateById(new CrmCustomerDO().setId(c.getId()).setAiHosted(false));
                return ChannelAiDraftDO.REASON_NOT_HOSTED;
            }
            return ChannelAiDraftDO.REASON_HUMAN_ACTIVE;
        }
        if (c != null) { // 已建档的客户需开启「AI 托管」才自动回复；新询盘默认托管
            if (!Boolean.TRUE.equals(c.getAiHosted())) {
                return ChannelAiDraftDO.REASON_NOT_HOSTED;
            }
            String cond = matchedCondition(h, c);
            if (cond != null) {
                return ChannelAiDraftDO.REASON_CONDITION;
            }
        }
        return null;
    }

    /** 「根据客户条件」转人工：返回命中的条件名称，没有命中返回 null */
    static String matchedCondition(ChannelAgentSettings.Handoff h, CrmCustomerDO c) {
        for (ChannelAgentSettings.Condition cond : h.getConditions()) {
            if (!cond.isEnabled() || cond.getField() == null) {
                continue;
            }
            boolean hit = switch (cond.getField()) {
                case "level" -> c.getLevel() != null && cond.getValues().contains(c.getLevel());
                case "source" -> c.getSource() != null && cond.getValues().contains(c.getSource());
                case "deal" -> Boolean.TRUE.equals(c.getDealStatus());
                default -> false;
            };
            if (hit) {
                return StrUtil.blankToDefault(cond.getName(), cond.getField());
            }
        }
        return null;
    }

    private static boolean humanRepliedRecently(List<LocalDateTime> humanTimes, ChannelAiConfigDO cfg) {
        if ("none".equals(ChannelAgentSettings.handoff(cfg).getTakeoverMode())) {
            return false; // 人工介入不影响 AI 托管
        }
        int hours = cfg.getHumanPauseHours() == null ? 12 : cfg.getHumanPauseHours();
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        return hours > 0 && humanTimes.stream().anyMatch(t -> t != null && t.isAfter(since));
    }

}
