package cn.iocoder.yudao.module.channel.service.reactivate;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.controller.admin.dashboard.vo.ChannelDashboardRespVO.Scope;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailSendReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.reactivate.vo.ChannelReactivateTaskCreateReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaMessageRespVO;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.ChannelWaSendReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiKnowledgeDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateItemDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.reactivate.ChannelReactivateTaskDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.ai.ChannelAiKnowledgeMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.reactivate.ChannelReactivateItemMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.reactivate.ChannelReactivateTaskMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import cn.iocoder.yudao.module.channel.service.dashboard.ChannelDashboardService;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 客户唤醒：找出久未联系的老客户 → AI 逐个写跟进邮件（WhatsApp 用已审核模板）→ 业务员确认 → 批量发出 → 跟踪回复。
 */
@Service
@Slf4j
public class ChannelReactivateService {

    public static final String CHANNEL_AUTO = "auto";
    public static final String CHANNEL_MAIL = "mail";
    public static final String CHANNEL_WHATSAPP = "whatsapp";
    private static final int MAX_CUSTOMERS = 200;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "channel-reactivate");
        t.setDaemon(true);
        return t;
    });

    @Resource
    private ChannelReactivateTaskMapper channelReactivateTaskMapper;
    @Resource
    private ChannelReactivateItemMapper channelReactivateItemMapper;
    @Resource
    private CrmCustomerMapper crmCustomerMapper;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;
    @Resource
    private ChannelMailMessageMapper channelMailMessageMapper;
    @Resource
    private ChannelWaMessageMapper channelWaMessageMapper;
    @Resource
    private ChannelAiKnowledgeMapper channelAiKnowledgeMapper;
    @Resource
    private ChannelDashboardService channelDashboardService;
    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private ChannelCreditService channelCreditService;
    @Resource
    private ChannelMailService channelMailService;
    @Resource
    private ChannelWhatsAppService channelWhatsAppService;

    /** 两次发送之间的间隔，避免短时间大量发信被邮件服务商判为垃圾邮件 */
    @Value("${cgch.reactivate.send-interval-ms:3000}")
    private long sendIntervalMs;

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    // ==================== 候选客户 ====================

    /** 可见范围内的业务员；null 表示不限 */
    private Set<Long> visibleUsers(Long loginUserId) {
        Scope s = channelDashboardService.resolveScope(loginUserId, null);
        return s.isAll() ? null : new HashSet<>(s.getUserIds());
    }

    /** 超过 inactiveDays 天未联系（从未联系的按建档时间算）、有邮箱或 WhatsApp 的客户 */
    public List<Map<String, Object>> candidates(Long loginUserId, int inactiveDays, Long ownerUserId, String keyword) {
        Set<Long> visible = visibleUsers(loginUserId);
        if (ownerUserId != null && visible != null && !visible.contains(ownerUserId)) {
            throw exception(DASHBOARD_USER_FORBIDDEN);
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(0, inactiveDays));
        LambdaQueryWrapperX<CrmCustomerDO> q = new LambdaQueryWrapperX<>();
        q.and(w -> w.lt(CrmCustomerDO::getContactLastTime, cutoff)
                .or(x -> x.isNull(CrmCustomerDO::getContactLastTime).lt(CrmCustomerDO::getCreateTime, cutoff)));
        q.and(w -> w.ne(CrmCustomerDO::getEmail, "").isNotNull(CrmCustomerDO::getEmail)
                .or(x -> x.ne(CrmCustomerDO::getWhatsapp, "").isNotNull(CrmCustomerDO::getWhatsapp)));
        q.eqIfPresent(CrmCustomerDO::getOwnerUserId, ownerUserId);
        if (visible != null) {
            q.in(CrmCustomerDO::getOwnerUserId, visible.isEmpty() ? List.of(-1L) : visible);
        }
        if (StrUtil.isNotBlank(keyword)) {
            q.and(w -> w.like(CrmCustomerDO::getName, keyword).or().like(CrmCustomerDO::getCompanyName, keyword));
        }
        q.last("LIMIT 500");
        List<CrmCustomerDO> list = crmCustomerMapper.selectList(q);
        Set<Long> inTask = list.isEmpty() ? Set.of() : channelReactivateItemMapper
                .selectActiveByCustomerIds(list.stream().map(CrmCustomerDO::getId).toList())
                .stream().map(ChannelReactivateItemDO::getCustomerId).collect(Collectors.toSet());
        List<Map<String, Object>> out = new ArrayList<>();
        for (CrmCustomerDO c : list) {
            LocalDateTime last = c.getContactLastTime() != null ? c.getContactLastTime() : c.getCreateTime();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("companyName", c.getCompanyName());
            m.put("countryCode", c.getCountryCode());
            m.put("ownerUserId", c.getOwnerUserId());
            m.put("email", c.getEmail());
            m.put("whatsapp", c.getWhatsapp());
            m.put("lastContactTime", last == null ? null : last.format(DAY));
            m.put("inactiveDays", last == null ? null : Duration.between(last, LocalDateTime.now()).toDays());
            m.put("contacted", c.getContactLastTime() != null);
            m.put("inTask", inTask.contains(c.getId()));
            out.add(m);
        }
        out.sort(Comparator.comparing(m -> -((Number) Objects.requireNonNullElse(m.get("inactiveDays"), 0L)).longValue()));
        return out;
    }

    // ==================== 创建任务 ====================

    public Long createTask(Long userId, ChannelReactivateTaskCreateReqVO req) {
        if (req.getCustomerIds() == null || req.getCustomerIds().isEmpty()) {
            throw exception(REACTIVATE_NO_CUSTOMER);
        }
        if (req.getCustomerIds().size() > MAX_CUSTOMERS) {
            throw exception(REACTIVATE_TOO_MANY, MAX_CUSTOMERS);
        }
        String channel = List.of(CHANNEL_MAIL, CHANNEL_WHATSAPP).contains(req.getChannel()) ? req.getChannel() : CHANNEL_AUTO;
        Set<Long> visible = visibleUsers(userId);
        List<CrmCustomerDO> customers = crmCustomerMapper.selectByIds(new LinkedHashSet<>(req.getCustomerIds()));
        if (visible != null && customers.stream().anyMatch(c -> !visible.contains(c.getOwnerUserId()))) {
            throw exception(DASHBOARD_USER_FORBIDDEN);
        }
        ChannelReactivateTaskDO task = ChannelReactivateTaskDO.builder()
                .name(StrUtil.blankToDefault(req.getName(), "客户唤醒 " + LocalDateTime.now().format(DAY)))
                .inactiveDays(req.getInactiveDays()).channel(channel).instructions(StrUtil.maxLength(req.getInstructions(), 1000))
                .waTemplateName(StrUtil.trimToNull(req.getWaTemplateName()))
                .waTemplateLanguage(StrUtil.blankToDefault(req.getWaTemplateLanguage(), "en_US"))
                .status(ChannelReactivateTaskDO.STATUS_GENERATING).userId(userId).build();
        channelReactivateTaskMapper.insert(task);
        for (CrmCustomerDO c : customers) {
            channelReactivateItemMapper.insert(plan(task, c, userId));
        }
        startGenerate(task.getId());
        return task.getId();
    }

    /** 按渠道偏好决定这位客户怎么发、用哪个账号；发不了的直接标为跳过并写明原因 */
    private ChannelReactivateItemDO plan(ChannelReactivateTaskDO task, CrmCustomerDO c, Long creatorId) {
        ChannelReactivateItemDO item = ChannelReactivateItemDO.builder().taskId(task.getId()).customerId(c.getId())
                .customerName(c.getName()).ownerUserId(c.getOwnerUserId())
                .lastContactTime(c.getContactLastTime() != null ? c.getContactLastTime() : c.getCreateTime()).credits(0L).build();
        boolean hasMail = StrUtil.isNotBlank(c.getEmail());
        boolean hasWa = StrUtil.isNotBlank(c.getWhatsapp());
        boolean useMail = CHANNEL_MAIL.equals(task.getChannel()) || (CHANNEL_AUTO.equals(task.getChannel()) && hasMail);
        if (useMail) {
            ChannelMailAccountDO a = pickMailAccount(c.getOwnerUserId(), creatorId);
            if (!hasMail) {
                return item.setChannel(CHANNEL_MAIL).setStatus(ChannelReactivateItemDO.STATUS_SKIPPED).setReasonDetail("客户没有邮箱");
            }
            if (a == null) {
                return item.setChannel(CHANNEL_MAIL).setRecipient(c.getEmail()).setStatus(ChannelReactivateItemDO.STATUS_SKIPPED)
                        .setReasonDetail("负责人没有可用的邮箱账号");
            }
            return item.setChannel(CHANNEL_MAIL).setRecipient(c.getEmail()).setAccountId(a.getId())
                    .setStatus(ChannelReactivateItemDO.STATUS_GENERATING);
        }
        item.setChannel(CHANNEL_WHATSAPP).setRecipient(c.getWhatsapp());
        if (!hasWa) {
            return item.setStatus(ChannelReactivateItemDO.STATUS_SKIPPED).setReasonDetail("客户没有邮箱和 WhatsApp");
        }
        if (task.getWaTemplateName() == null) {
            return item.setStatus(ChannelReactivateItemDO.STATUS_SKIPPED)
                    .setReasonDetail("超过 24 小时只能发送已审核的模板，请在任务中选择 WhatsApp 模板");
        }
        ChannelWaAccountDO a = pickWaAccount(c.getOwnerUserId(), creatorId);
        if (a == null) {
            return item.setStatus(ChannelReactivateItemDO.STATUS_SKIPPED).setReasonDetail("负责人没有可用的 WhatsApp 账号");
        }
        return item.setAccountId(a.getId()).setStatus(ChannelReactivateItemDO.STATUS_DRAFT)
                .setContent("[WhatsApp 模板] " + task.getWaTemplateName() + "（" + task.getWaTemplateLanguage() + "）");
    }

    private ChannelMailAccountDO pickMailAccount(Long ownerId, Long creatorId) {
        for (Long uid : new LinkedHashSet<>(Arrays.asList(ownerId, creatorId))) {
            if (uid == null) {
                continue;
            }
            Optional<ChannelMailAccountDO> a = channelMailAccountMapper.selectListByUserId(uid).stream()
                    .filter(x -> Boolean.TRUE.equals(x.getEnabled())).findFirst();
            if (a.isPresent()) {
                return a.get();
            }
        }
        return null;
    }

    private ChannelWaAccountDO pickWaAccount(Long ownerId, Long creatorId) {
        for (Long uid : new LinkedHashSet<>(Arrays.asList(ownerId, creatorId))) {
            if (uid == null) {
                continue;
            }
            Optional<ChannelWaAccountDO> a = channelWaAccountMapper.selectListByUserId(uid).stream()
                    .filter(x -> Boolean.TRUE.equals(x.getEnabled())).findFirst();
            if (a.isPresent()) {
                return a.get();
            }
        }
        return null;
    }

    // ==================== AI 生成 ====================

    private void startGenerate(Long taskId) {
        Long tenantId = TenantContextHolder.getTenantId();
        executor.submit(() -> {
            try {
                TenantUtils.execute(tenantId, () -> generateAll(taskId));
            } catch (Exception e) {
                log.warn("[startGenerate][任务({}) 生成失败] {}", taskId, e.getMessage());
            }
        });
    }

    private void generateAll(Long taskId) {
        ChannelReactivateTaskDO task = channelReactivateTaskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        for (ChannelReactivateItemDO item : channelReactivateItemMapper.selectListByTaskIdAndStatus(taskId, ChannelReactivateItemDO.STATUS_GENERATING)) {
            generateOne(task, item);
        }
        channelReactivateTaskMapper.updateById(new ChannelReactivateTaskDO().setId(taskId).setStatus(ChannelReactivateTaskDO.STATUS_READY));
    }

    private void generateOne(ChannelReactivateTaskDO task, ChannelReactivateItemDO item) {
        ChannelAiConfigDO cfg = channelAiService.getConfig();
        if (StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
            fail(item, "请先在数字员工设置中配置大模型");
            return;
        }
        if (!channelCreditService.hasBalance()) {
            fail(item, "智点余额不足");
            return;
        }
        CrmCustomerDO c = crmCustomerMapper.selectById(item.getCustomerId());
        if (c == null) {
            fail(item, "客户不存在");
            return;
        }
        try {
            LlmClient.Result r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(), List.of(
                    LlmClient.Message.system(systemPrompt(cfg)), LlmClient.Message.user(userPrompt(task, c, item))), 0.6);
            String[] sb = parse(r.content());
            String body = sb[1];
            if (StrUtil.isNotBlank(cfg.getMailSignature())) {
                body = body.trim() + "\n\n" + cfg.getMailSignature().trim();
            }
            long credits = channelCreditService.consume("reactivate", item.getId(), item.getOwnerUserId(),
                    r.promptTokens() + r.completionTokens(), "客户唤醒");
            channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(item.getId())
                    .setSubject(StrUtil.maxLength(sb[0], 200)).setContent(body).setCredits(credits)
                    .setStatus(ChannelReactivateItemDO.STATUS_DRAFT).setReasonDetail(""));
        } catch (Exception e) {
            fail(item, StrUtil.maxLength("AI 生成失败：" + e.getMessage(), 250));
        }
    }

    private void fail(ChannelReactivateItemDO item, String reason) {
        channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(item.getId())
                .setStatus(ChannelReactivateItemDO.STATUS_FAILED).setReasonDetail(reason));
    }

    private static String systemPrompt(ChannelAiConfigDO cfg) {
        return "你是外贸企业的销售代表「" + StrUtil.blankToDefault(cfg.getAgentName(), "Sales") + "」，要给一位很久没有联系的老客户写一封跟进邮件（客户唤醒）。\n"
                + "要求：\n1. 用客户所在国家的常用商务语言；无法判断时用 " + StrUtil.blankToDefault(cfg.getDefaultLanguage(), "English") + "。\n"
                + "2. 自然地提起上次的联系，表达问候，给出一个具体、对客户有价值的理由（新品、行情、样品、优惠等，只能使用提供的资料和写作要求，不得编造价格、交期、折扣）。\n"
                + "3. 结尾提出一个简单、容易回复的问题。篇幅 80~150 词，不要写签名。\n"
                + "4. 只输出 JSON：{\"subject\": \"邮件主题\", \"body\": \"邮件正文\"}，不要输出其他内容。";
    }

    private String userPrompt(ChannelReactivateTaskDO task, CrmCustomerDO c, ChannelReactivateItemDO item) {
        StringBuilder sb = new StringBuilder();
        sb.append("客户名称：").append(c.getName()).append("\n");
        if (StrUtil.isNotBlank(c.getCompanyName())) {
            sb.append("公司：").append(c.getCompanyName()).append("\n");
        }
        if (StrUtil.isNotBlank(c.getCountryCode())) {
            sb.append("国家：").append(c.getCountryCode()).append("\n");
        }
        if (item.getLastContactTime() != null) {
            sb.append("上次联系：").append(item.getLastContactTime().format(DAY)).append("\n");
        }
        if (StrUtil.isNotBlank(c.getContactLastContent())) {
            sb.append("上次联系内容：").append(StrUtil.maxLength(c.getContactLastContent(), 300)).append("\n");
        }
        if (StrUtil.isNotBlank(task.getInstructions())) {
            sb.append("写作要求：").append(task.getInstructions()).append("\n");
        }
        String company = channelAiKnowledgeMapper.selectEnabled().stream()
                .filter(k -> ChannelAiKnowledgeDO.TYPE_COMPANY.equals(k.getType()))
                .map(k -> StrUtil.nullToEmpty(k.getContent())).collect(Collectors.joining("\n"));
        if (StrUtil.isNotBlank(company)) {
            sb.append("我方企业介绍：").append(StrUtil.maxLength(company, 1500)).append("\n");
        }
        return sb.toString();
    }

    /** 解析 {"subject","body"}；模型没按格式返回时整段作为正文 */
    static String[] parse(String content) {
        String s = StrUtil.nullToEmpty(content);
        int i = s.indexOf('{'), j = s.lastIndexOf('}');
        if (i >= 0 && j > i) {
            try {
                JsonNode n = JSON.readTree(s.substring(i, j + 1));
                String subject = n.path("subject").asText("");
                String body = n.path("body").asText("");
                if (StrUtil.isNotBlank(body)) {
                    return new String[]{StrUtil.blankToDefault(subject, "Following up"), body};
                }
            } catch (Exception ignored) {
                // 落到下面
            }
        }
        return new String[]{"Following up", s.trim()};
    }

    // ==================== 确认与发送 ====================

    private ChannelReactivateTaskDO validateTask(Long id) {
        ChannelReactivateTaskDO t = channelReactivateTaskMapper.selectById(id);
        if (t == null) {
            throw exception(REACTIVATE_TASK_NOT_EXISTS);
        }
        return t;
    }

    private ChannelReactivateItemDO validateItem(Long id) {
        ChannelReactivateItemDO i = channelReactivateItemMapper.selectById(id);
        if (i == null) {
            throw exception(REACTIVATE_ITEM_NOT_EXISTS);
        }
        return i;
    }

    public void updateItem(Long id, String subject, String content) {
        ChannelReactivateItemDO i = validateItem(id);
        if (!List.of(ChannelReactivateItemDO.STATUS_DRAFT, ChannelReactivateItemDO.STATUS_APPROVED).contains(i.getStatus())) {
            throw exception(REACTIVATE_ITEM_STATUS, "只有待确认或已确认未发送的内容可以修改");
        }
        if (CHANNEL_MAIL.equals(i.getChannel()) && StrUtil.isBlank(content)) {
            throw exception(REACTIVATE_ITEM_STATUS, "正文不能为空");
        }
        ChannelReactivateItemDO u = new ChannelReactivateItemDO().setId(id);
        if (CHANNEL_MAIL.equals(i.getChannel())) {
            u.setSubject(StrUtil.maxLength(subject, 200)).setContent(content);
        }
        channelReactivateItemMapper.updateById(u);
    }

    /** 批量改状态：approve 确认、skip 跳过、draft 撤回确认 */
    public int changeStatus(Collection<Long> ids, String action) {
        int n = 0;
        for (Long id : ids) {
            ChannelReactivateItemDO i = channelReactivateItemMapper.selectById(id);
            if (i == null) {
                continue;
            }
            String to = null;
            if ("approve".equals(action) && ChannelReactivateItemDO.STATUS_DRAFT.equals(i.getStatus())) {
                to = ChannelReactivateItemDO.STATUS_APPROVED;
            } else if ("draft".equals(action) && ChannelReactivateItemDO.STATUS_APPROVED.equals(i.getStatus())) {
                to = ChannelReactivateItemDO.STATUS_DRAFT;
            } else if ("skip".equals(action) && List.of(ChannelReactivateItemDO.STATUS_DRAFT, ChannelReactivateItemDO.STATUS_APPROVED,
                    ChannelReactivateItemDO.STATUS_FAILED).contains(i.getStatus())) {
                to = ChannelReactivateItemDO.STATUS_SKIPPED;
            }
            if (to != null) {
                channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(id).setStatus(to));
                n++;
            }
        }
        return n;
    }

    /** 重新生成一条（失败或不满意时） */
    public void regenerate(Long id) {
        ChannelReactivateItemDO i = validateItem(id);
        if (!CHANNEL_MAIL.equals(i.getChannel()) || !List.of(ChannelReactivateItemDO.STATUS_DRAFT,
                ChannelReactivateItemDO.STATUS_FAILED, ChannelReactivateItemDO.STATUS_APPROVED).contains(i.getStatus()) || i.getAccountId() == null) {
            throw exception(REACTIVATE_ITEM_STATUS, "只有邮件且未发送的内容可以重新生成");
        }
        channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(id).setStatus(ChannelReactivateItemDO.STATUS_GENERATING));
        ChannelReactivateTaskDO task = validateTask(i.getTaskId());
        Long tenantId = TenantContextHolder.getTenantId();
        executor.submit(() -> TenantUtils.execute(tenantId, () -> generateOne(task, validateItem(id))));
    }

    /** 发送任务中所有「已确认」的内容，后台逐条发出，返回将要发送的条数 */
    public int send(Long taskId) {
        validateTask(taskId);
        int n = channelReactivateItemMapper.selectListByTaskIdAndStatus(taskId, ChannelReactivateItemDO.STATUS_APPROVED).size();
        if (n == 0) {
            throw exception(REACTIVATE_ITEM_STATUS, "没有已确认的内容，请先确认要发送的客户");
        }
        channelReactivateTaskMapper.updateById(new ChannelReactivateTaskDO().setId(taskId).setStatus(ChannelReactivateTaskDO.STATUS_SENDING));
        Long tenantId = TenantContextHolder.getTenantId();
        executor.submit(() -> {
            try {
                TenantUtils.execute(tenantId, () -> sendAll(taskId));
            } catch (Exception e) {
                log.warn("[send][任务({}) 发送中断] {}", taskId, e.getMessage());
            }
        });
        return n;
    }

    private void sendAll(Long taskId) {
        ChannelReactivateTaskDO task = channelReactivateTaskMapper.selectById(taskId);
        List<ChannelReactivateItemDO> items = channelReactivateItemMapper.selectListByTaskIdAndStatus(taskId, ChannelReactivateItemDO.STATUS_APPROVED);
        for (int k = 0; k < items.size(); k++) {
            ChannelReactivateItemDO i = items.get(k);
            try {
                Long ref = CHANNEL_MAIL.equals(i.getChannel()) ? sendMail(i) : sendWhatsApp(task, i);
                channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(i.getId())
                        .setStatus(ChannelReactivateItemDO.STATUS_SENT).setSentTime(LocalDateTime.now()).setSentRefId(ref).setReasonDetail(""));
            } catch (Exception e) {
                channelReactivateItemMapper.updateById(new ChannelReactivateItemDO().setId(i.getId())
                        .setStatus(ChannelReactivateItemDO.STATUS_FAILED).setReasonDetail(StrUtil.maxLength("发送失败：" + e.getMessage(), 250)));
            }
            if (sendIntervalMs > 0 && k < items.size() - 1) {
                try {
                    Thread.sleep(sendIntervalMs);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        boolean pending = !channelReactivateItemMapper.selectListByTaskIdAndStatus(taskId, ChannelReactivateItemDO.STATUS_DRAFT).isEmpty();
        channelReactivateTaskMapper.updateById(new ChannelReactivateTaskDO().setId(taskId)
                .setStatus(pending ? ChannelReactivateTaskDO.STATUS_READY : ChannelReactivateTaskDO.STATUS_DONE));
    }

    private Long sendMail(ChannelReactivateItemDO i) {
        ChannelMailAccountDO a = channelMailAccountMapper.selectById(i.getAccountId());
        if (a == null) {
            throw new IllegalStateException("邮箱账号不存在");
        }
        ChannelMailSendReqVO req = new ChannelMailSendReqVO();
        req.setAccountId(a.getId());
        req.setTo(List.of(i.getRecipient()));
        req.setSubject(i.getSubject());
        req.setText(i.getContent());
        return channelMailService.send(a.getUserId(), req, true);
    }

    private Long sendWhatsApp(ChannelReactivateTaskDO task, ChannelReactivateItemDO i) {
        ChannelWaAccountDO a = channelWaAccountMapper.selectById(i.getAccountId());
        if (a == null) {
            throw new IllegalStateException("WhatsApp 账号不存在");
        }
        ChannelWaSendReqVO req = new ChannelWaSendReqVO();
        req.setAccountId(a.getId());
        req.setTo(i.getRecipient());
        req.setTemplateName(task.getWaTemplateName());
        req.setTemplateLanguage(task.getWaTemplateLanguage());
        req.setTemplatePreview(i.getContent());
        ChannelWaMessageRespVO m = channelWhatsAppService.send(a.getUserId(), req, true);
        return m.getId();
    }

    // ==================== 查询 ====================

    public PageResult<ChannelReactivateTaskDO> taskPage(PageParam req) {
        return channelReactivateTaskMapper.selectPage(req);
    }

    public ChannelReactivateTaskDO getTask(Long id) {
        return validateTask(id);
    }

    public void deleteTask(Long id) {
        ChannelReactivateTaskDO t = validateTask(id);
        if (ChannelReactivateTaskDO.STATUS_SENDING.equals(t.getStatus())) {
            throw exception(REACTIVATE_ITEM_STATUS, "正在发送中，不能删除");
        }
        channelReactivateItemMapper.delete(ChannelReactivateItemDO::getTaskId, id);
        channelReactivateTaskMapper.deleteById(id);
    }

    public List<ChannelReactivateItemDO> items(Long taskId) {
        return channelReactivateItemMapper.selectListByTaskId(taskId);
    }

    /** 已发送的客户在发送后是否回复过 */
    public boolean replied(ChannelReactivateItemDO i) {
        if (!ChannelReactivateItemDO.STATUS_SENT.equals(i.getStatus()) || i.getSentTime() == null) {
            return false;
        }
        if (CHANNEL_MAIL.equals(i.getChannel())) {
            return channelMailMessageMapper.selectCount(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                    .eq(ChannelMailMessageDO::getCustomerId, i.getCustomerId())
                    .eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_IN)
                    .gt(ChannelMailMessageDO::getSentTime, i.getSentTime())) > 0;
        }
        ChannelWaMessageDO sent = i.getSentRefId() == null ? null : channelWaMessageMapper.selectById(i.getSentRefId());
        return sent != null && channelWaMessageMapper.selectCount(new LambdaQueryWrapperX<ChannelWaMessageDO>()
                .eq(ChannelWaMessageDO::getConversationId, sent.getConversationId())
                .eq(ChannelWaMessageDO::getDirection, ChannelWaMessageDO.DIRECTION_IN)
                .gt(ChannelWaMessageDO::getId, sent.getId())) > 0;
    }

    public static String toJson(Object o) {
        return JsonUtils.toJsonString(o);
    }

}
