package cn.iocoder.yudao.module.channel.service.mailbatch;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.ChannelMailSendReqVO;
import cn.iocoder.yudao.module.channel.controller.admin.mailbatch.vo.ChannelMailBatchCreateReqVO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch.ChannelMailBatchDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mailbatch.ChannelMailBatchItemDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.policy.ChannelSuppressionDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mailbatch.ChannelMailBatchItemMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mailbatch.ChannelMailBatchMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.mail.ChannelMailService;
import cn.iocoder.yudao.module.channel.service.policy.ChannelSendPolicyService;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.service.customer.CrmCustomerService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 邮件群发：选客户（或直接填邮箱）→ 验证邮箱（格式、域名能否收信、退订 / 黑名单）→ 按每分钟速率从多个邮箱轮流发出。
 * 每封带退订链接和 List-Unsubscribe 邮件头，退订的地址加入退订名单，以后所有发送都会跳过。
 * 发送由定时任务推进（{@link #tick()}），服务重启后自动接着发。
 */
@Service
@Slf4j
public class ChannelMailBatchService {

    public static final String UNSUBSCRIBE_PATH = "/admin-api/channel/mail-batch/unsubscribe";
    private static final int MAX_RECIPIENTS = 2000;
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** 达到邮箱的发送上限时，过多久再试 */
    private static final Duration RATE_LIMIT_BACKOFF = Duration.ofMinutes(10);

    @Resource
    private ChannelMailBatchMapper batchMapper;
    @Resource
    private ChannelMailBatchItemMapper itemMapper;
    @Resource
    private ChannelMailMessageMapper mailMessageMapper;
    @Resource
    private ChannelMailService channelMailService;
    @Resource
    private ChannelSendPolicyService channelSendPolicyService;
    @Resource
    private CrmCustomerService crmCustomerService;
    @Resource
    private ChannelOAuthProperties channelOAuthProperties;

    /** 不做域名收信检查的域名（测试环境用），逗号分隔 */
    @Value("${cgch.mail-batch.skip-mx-domains:}")
    private String skipMxDomains;

    private final Map<String, Boolean> domainCache = new ConcurrentHashMap<>();

    // ==================== 新建与验证 ====================

    /** 新建群发（草稿）：整理收件人并逐个验证，返回任务编号；确认后调用 start 开始发送 */
    public Long create(Long userId, ChannelMailBatchCreateReqVO req) {
        List<Long> accounts = req.getAccountIds().stream().filter(Objects::nonNull).distinct().toList();
        for (Long id : accounts) {
            channelMailService.validateAccount(userId, id); // 只能用自己的邮箱发
        }
        if (accounts.isEmpty()) {
            throw exception(MAIL_BATCH_INVALID, "请选择发件邮箱");
        }
        // 收件人：CRM 客户（有查看权限的）+ 直接填的邮箱，按邮箱去重
        Map<String, ChannelMailBatchItemDO> byEmail = new LinkedHashMap<>();
        List<String> noEmail = new ArrayList<>();
        for (Long cid : Optional.ofNullable(req.getCustomerIds()).orElse(List.of())) {
            CrmCustomerDO c;
            try {
                c = crmCustomerService.validateCustomerReadable(cid);
            } catch (Exception e) {
                continue; // 没有查看权限的客户跳过
            }
            String email = StrUtil.trim(c.getEmail());
            if (StrUtil.isBlank(email)) {
                noEmail.add(c.getName());
                continue;
            }
            byEmail.putIfAbsent(email.toLowerCase(Locale.ROOT), ChannelMailBatchItemDO.builder().customerId(c.getId())
                    .name(c.getName()).company(StrUtil.blankToDefault(c.getCompanyName(), c.getName())).email(email).build());
        }
        for (String raw : Optional.ofNullable(req.getEmails()).orElse(List.of())) {
            for (String e : StrUtil.nullToEmpty(raw).split("[,;，；\\s]+")) {
                String email = StrUtil.trim(e);
                if (!email.isEmpty()) {
                    byEmail.putIfAbsent(email.toLowerCase(Locale.ROOT), ChannelMailBatchItemDO.builder().email(email)
                            .name(StrUtil.subBefore(email, "@", false)).build());
                }
            }
        }
        if (byEmail.isEmpty()) {
            throw exception(MAIL_BATCH_INVALID, noEmail.isEmpty() ? "请选择客户或填写邮箱" : "选中的客户都没有填写邮箱");
        }
        if (byEmail.size() > MAX_RECIPIENTS) {
            throw exception(MAIL_BATCH_INVALID, "一次最多 " + MAX_RECIPIENTS + " 个收件人");
        }
        String type = ChannelMailBatchDO.TYPE_HTML.equals(req.getContentType()) ? ChannelMailBatchDO.TYPE_HTML : ChannelMailBatchDO.TYPE_TEXT;
        ChannelMailBatchDO b = ChannelMailBatchDO.builder().name(StrUtil.maxLength(StrUtil.trim(req.getName()), 100))
                .subject(StrUtil.maxLength(StrUtil.trim(req.getSubject()), 250)).content(req.getContent()).contentType(type)
                .accountIds(StrUtil.join(",", accounts)).ratePerMinute(req.getRatePerMinute() == null ? 5 : req.getRatePerMinute())
                .status(ChannelMailBatchDO.STATUS_DRAFT).total(byEmail.size()).userId(userId).build();
        batchMapper.insert(b);
        int k = 0;
        for (ChannelMailBatchItemDO i : byEmail.values()) {
            i.setBatchId(b.getId());
            i.setAccountId(accounts.get(k++ % accounts.size())); // 多个邮箱轮流
            i.setToken(IdUtil.fastSimpleUUID());
            i.setUnsubscribed(false);
            String problem = checkEmail(i.getEmail());
            if (problem != null) {
                i.setStatus(ChannelMailBatchItemDO.STATUS_INVALID);
                i.setReason(problem);
            } else if (channelSendPolicyService.isMailSuppressed(i.getEmail())) {
                i.setStatus(ChannelMailBatchItemDO.STATUS_SUPPRESSED);
                i.setReason("已退订或在黑名单中");
            } else {
                i.setStatus(ChannelMailBatchItemDO.STATUS_PENDING);
            }
            itemMapper.insert(i);
        }
        return b.getId();
    }

    /** 邮箱有问题时返回原因：格式不对、域名不存在或不能收信 */
    String checkEmail(String email) {
        if (!EMAIL.matcher(email).matches()) {
            return "邮箱格式不对";
        }
        String domain = StrUtil.subAfter(email, "@", true).toLowerCase(Locale.ROOT);
        if (StrUtil.split(StrUtil.nullToEmpty(skipMxDomains), ',').stream().map(String::trim).anyMatch(domain::equals)) {
            return null;
        }
        Boolean ok = domainCache.computeIfAbsent(domain, ChannelMailBatchService::domainReceivesMail);
        return ok ? null : "域名 " + domain + " 不存在或不能收信";
    }

    /** 查 DNS：有 MX 记录（或没有 MX 但有 A 记录）算能收信；查询本身出错（网络问题）时不拦 */
    static boolean domainReceivesMail(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "3000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
            try {
                javax.naming.directory.Attributes attrs = ctx.getAttributes(domain, new String[]{"MX", "A"});
                javax.naming.directory.Attribute mx = attrs.get("MX");
                javax.naming.directory.Attribute a = attrs.get("A");
                if (mx != null && mx.size() > 0) {
                    String first = String.valueOf(mx.get(0)).trim();
                    return !first.endsWith(" .") && !"0 .".equals(first); // RFC 7505「不收信」MX
                }
                return a != null && a.size() > 0;
            } finally {
                ctx.close();
            }
        } catch (javax.naming.NameNotFoundException e) {
            return false; // 域名不存在
        } catch (Exception e) {
            log.info("[domainReceivesMail][{} 查询失败，按能收信处理] {}", domain, e.getMessage());
            return true;
        }
    }

    // ==================== 发送 ====================

    public void start(Long userId, Long id) {
        ChannelMailBatchDO b = validate(userId, id);
        if (!ChannelMailBatchDO.STATUS_DRAFT.equals(b.getStatus())) {
            throw exception(MAIL_BATCH_INVALID, "只有草稿可以开始发送");
        }
        if (itemMapper.selectNextPending(id) == null) {
            throw exception(MAIL_BATCH_INVALID, "没有可以发送的收件人");
        }
        LocalDateTime now = LocalDateTime.now();
        batchMapper.updateById(new ChannelMailBatchDO().setId(id).setStatus(ChannelMailBatchDO.STATUS_SENDING)
                .setStartTime(now).setNextSendTime(now));
    }

    /** 取消：没发的标为已取消，已发的保留 */
    public void cancel(Long userId, Long id) {
        ChannelMailBatchDO b = validate(userId, id);
        if (ChannelMailBatchDO.STATUS_DONE.equals(b.getStatus()) || ChannelMailBatchDO.STATUS_CANCELLED.equals(b.getStatus())) {
            return;
        }
        batchMapper.updateById(new ChannelMailBatchDO().setId(id).setStatus(ChannelMailBatchDO.STATUS_CANCELLED)
                .setFinishTime(LocalDateTime.now()));
        itemMapper.update(new ChannelMailBatchItemDO().setStatus(ChannelMailBatchItemDO.STATUS_CANCELLED),
                new LambdaQueryWrapperX<ChannelMailBatchItemDO>().eq(ChannelMailBatchItemDO::getBatchId, id)
                        .eq(ChannelMailBatchItemDO::getStatus, ChannelMailBatchItemDO.STATUS_PENDING));
    }

    public void delete(Long userId, Long id) {
        ChannelMailBatchDO b = validate(userId, id);
        if (ChannelMailBatchDO.STATUS_SENDING.equals(b.getStatus())) {
            throw exception(MAIL_BATCH_INVALID, "正在发送中，请先取消");
        }
        itemMapper.delete(ChannelMailBatchItemDO::getBatchId, id);
        batchMapper.deleteById(id);
    }

    /** 定时调用：每个到点的任务按速率发出下一封（每次最多补发几封，避免停机后一下子发太多） */
    public void tick() {
        List<ChannelMailBatchDO> due = TenantUtils.executeIgnore(() -> batchMapper.selectDue(LocalDateTime.now(), 20));
        for (ChannelMailBatchDO b : due) {
            try {
                TenantUtils.execute(b.getTenantId(), () -> sendNext(b));
            } catch (Exception e) {
                log.warn("[tick][群发任务 {} 发送失败] {}", b.getId(), e.getMessage());
            }
        }
    }

    private void sendNext(ChannelMailBatchDO b) {
        long gapMs = 60_000L / Math.max(1, Math.min(60, b.getRatePerMinute() == null ? 5 : b.getRatePerMinute()));
        LocalDateTime now = LocalDateTime.now();
        for (int n = 0; n < 3; n++) {
            ChannelMailBatchDO fresh = batchMapper.selectById(b.getId());
            if (fresh == null || !ChannelMailBatchDO.STATUS_SENDING.equals(fresh.getStatus())
                    || fresh.getNextSendTime() == null || fresh.getNextSendTime().isAfter(now)) {
                return;
            }
            ChannelMailBatchItemDO i = itemMapper.selectNextPending(b.getId());
            if (i == null) {
                batchMapper.updateById(new ChannelMailBatchDO().setId(b.getId()).setStatus(ChannelMailBatchDO.STATUS_DONE)
                        .setFinishTime(now).setNextSendTime(null));
                return;
            }
            LocalDateTime next = fresh.getNextSendTime().plus(Duration.ofMillis(gapMs));
            if (next.isBefore(now)) {
                next = now; // 停机后恢复：从现在起按速率发，不一次补发
            }
            try {
                Long ref = sendOne(fresh, i);
                itemMapper.updateById(new ChannelMailBatchItemDO().setId(i.getId()).setStatus(ChannelMailBatchItemDO.STATUS_SENT)
                        .setSentTime(LocalDateTime.now()).setSentRefId(ref).setReason(""));
            } catch (ServiceException e) {
                if (Objects.equals(e.getCode(), SEND_RATE_LIMITED.getCode())) {
                    // 邮箱达到发送上限：这封保留，过一会儿再发
                    batchMapper.updateById(new ChannelMailBatchDO().setId(b.getId()).setNextSendTime(now.plus(RATE_LIMIT_BACKOFF)));
                    log.info("[sendNext][群发任务 {} 达到发送上限，{} 分钟后继续] {}", b.getId(), RATE_LIMIT_BACKOFF.toMinutes(), e.getMessage());
                    return;
                }
                boolean suppressed = Objects.equals(e.getCode(), SEND_SUPPRESSED.getCode());
                itemMapper.updateById(new ChannelMailBatchItemDO().setId(i.getId())
                        .setStatus(suppressed ? ChannelMailBatchItemDO.STATUS_SUPPRESSED : ChannelMailBatchItemDO.STATUS_FAILED)
                        .setReason(StrUtil.maxLength(suppressed ? "已退订或在黑名单中" : e.getMessage(), 250)));
            } catch (Exception e) {
                itemMapper.updateById(new ChannelMailBatchItemDO().setId(i.getId()).setStatus(ChannelMailBatchItemDO.STATUS_FAILED)
                        .setReason(StrUtil.maxLength(StrUtil.blankToDefault(e.getMessage(), "发送失败"), 250)));
            }
            batchMapper.updateById(new ChannelMailBatchDO().setId(b.getId()).setNextSendTime(next));
            if (next.isAfter(now)) {
                return;
            }
        }
    }

    private Long sendOne(ChannelMailBatchDO b, ChannelMailBatchItemDO i) {
        String url = unsubscribeUrl(i.getToken());
        boolean html = ChannelMailBatchDO.TYPE_HTML.equals(b.getContentType());
        String body = fill(b.getContent(), i, html);
        ChannelMailSendReqVO req = new ChannelMailSendReqVO();
        req.setAccountId(i.getAccountId());
        req.setTo(List.of(i.getEmail()));
        req.setSubject(fill(b.getSubject(), i, false));
        if (html) {
            req.setHtml(body + "<p style=\"margin-top:24px;font-size:12px;color:#888\">If you no longer want to receive these emails, "
                    + "<a href=\"" + url + "\" style=\"color:#888\">unsubscribe here</a>.</p>");
        } else {
            req.setText(StrUtil.nullToEmpty(body).stripTrailing() + "\n\n--\nIf you no longer want to receive these emails, unsubscribe: " + url);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("List-Unsubscribe", "<" + url + ">");
        headers.put("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        req.setHeaders(headers);
        ChannelMailAccountDO a = channelMailService.getAccountInternal(i.getAccountId());
        if (a == null) {
            throw new IllegalStateException("发件邮箱已解绑");
        }
        return channelMailService.send(a.getUserId(), req, true); // 按批量发送计入频率上限，并检查退订名单
    }

    /** {name} {company} 换成收件人的称呼和公司；HTML 正文里转义 */
    static String fill(String tpl, ChannelMailBatchItemDO i, boolean html) {
        String name = StrUtil.blankToDefault(i.getName(), "there");
        String company = StrUtil.blankToDefault(i.getCompany(), "your company");
        if (html) {
            name = escape(name);
            company = escape(company);
        }
        return StrUtil.nullToEmpty(tpl).replace("{name}", name).replace("{company}", company);
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    String unsubscribeUrl(String token) {
        return StrUtil.removeSuffix(StrUtil.trim(channelOAuthProperties.getRedirectBase()), "/") + UNSUBSCRIBE_PATH + "?t=" + token;
    }

    // ==================== 退订 ====================

    /** 收件人点退订链接：加入退订名单，以后所有邮件都不再发给这个地址。返回邮箱，令牌无效时返回 null */
    public String unsubscribe(String token) {
        if (StrUtil.isBlank(token) || token.length() > 32) {
            return null;
        }
        ChannelMailBatchItemDO i = TenantUtils.executeIgnore(() -> itemMapper.selectByToken(token));
        if (i == null) {
            return null;
        }
        TenantUtils.execute(i.getTenantId(), () -> {
            channelSendPolicyService.add(ChannelSendPolicyService.CHANNEL_MAIL, i.getEmail(), ChannelSuppressionDO.REASON_UNSUBSCRIBE,
                    "点击群发邮件里的退订链接（任务 " + i.getBatchId() + "）", null);
            itemMapper.updateById(new ChannelMailBatchItemDO().setId(i.getId()).setUnsubscribed(true));
        });
        log.info("[unsubscribe][{} 通过群发任务 {} 退订]", i.getEmail(), i.getBatchId());
        return i.getEmail();
    }

    // ==================== 查询 ====================

    public PageResult<ChannelMailBatchDO> page(Long userId, PageParam req) {
        return batchMapper.selectPage(req, userId);
    }

    public ChannelMailBatchDO validate(Long userId, Long id) {
        ChannelMailBatchDO b = id == null ? null : batchMapper.selectById(id);
        if (b == null || !Objects.equals(b.getUserId(), userId)) {
            throw exception(MAIL_BATCH_NOT_EXISTS);
        }
        return b;
    }

    public List<ChannelMailBatchItemDO> items(Long userId, Long id, String status) {
        validate(userId, id);
        return itemMapper.selectListByBatch(id, StrUtil.emptyToNull(status));
    }

    public Map<String, Integer> counts(Long id) {
        return itemMapper.countByStatus(id);
    }

    /** 已发送的收件人里，发出后回过信的（按发件地址匹配） */
    public Set<Long> repliedItemIds(List<ChannelMailBatchItemDO> items) {
        Set<Long> out = new HashSet<>();
        for (ChannelMailBatchItemDO i : items) {
            if (ChannelMailBatchItemDO.STATUS_SENT.equals(i.getStatus()) && i.getSentRefId() != null
                    && mailMessageMapper.selectCount(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                    .eq(ChannelMailMessageDO::getDirection, ChannelMailMessageDO.DIRECTION_IN)
                    .eq(ChannelMailMessageDO::getFromAddress, i.getEmail())
                    .gt(ChannelMailMessageDO::getId, i.getSentRefId())) > 0) {
                out.add(i.getId());
            }
        }
        return out;
    }

}
