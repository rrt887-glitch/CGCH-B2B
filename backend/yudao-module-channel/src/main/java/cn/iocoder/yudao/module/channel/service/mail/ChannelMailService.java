package cn.iocoder.yudao.module.channel.service.mail;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.*;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAttachmentDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAttachmentMapper;
import cn.iocoder.yudao.module.channel.service.file.ChannelFileHelper;
import org.springframework.web.multipart.MultipartFile;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * 邮箱渠道：账号管理、收信同步、发信、邮件查询。
 * 账号归属于业务员本人，所有接口只能访问自己的邮箱。
 */
@Service
@Slf4j
public class ChannelMailService {

    private static final int SNIPPET_LENGTH = 160;

    @Resource
    private cn.iocoder.yudao.module.channel.service.oauth.ChannelMailOAuthService channelMailOAuthService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.policy.ChannelSendPolicyService channelSendPolicyService;
    @Resource
    private ChannelMailAccountMapper accountMapper;
    @Resource
    private ChannelMailMessageMapper messageMapper;
    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmClueMapper crmClueMapper;
    @Resource
    private ChannelMailAttachmentMapper attachmentMapper;
    @Resource
    private ChannelFileHelper fileHelper;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    // ==================== 账号 ====================

    public Long createAccount(Long userId, ChannelMailAccountSaveReqVO req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (accountMapper.selectByEmail(email) != null) {
            throw exception(MAIL_ACCOUNT_EMAIL_EXISTS);
        }
        ChannelMailAccountDO a = BeanUtils.toBean(req, ChannelMailAccountDO.class)
                .setId(null).setUserId(userId).setEmail(email)
                .setEnabled(req.getEnabled() == null || req.getEnabled());
        normalize(a);
        accountMapper.insert(a);
        return a.getId();
    }

    public void updateAccount(Long userId, ChannelMailAccountSaveReqVO req) {
        ChannelMailAccountDO old = validateAccount(userId, req.getId());
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        ChannelMailAccountDO same = accountMapper.selectByEmail(email);
        if (same != null && !same.getId().equals(old.getId())) {
            throw exception(MAIL_ACCOUNT_EMAIL_EXISTS);
        }
        ChannelMailAccountDO a = BeanUtils.toBean(req, ChannelMailAccountDO.class).setEmail(email);
        if (StrUtil.isBlank(req.getPassword())) {
            a.setPassword(null); // 不填表示不修改，updateById 会忽略 null 字段
        }
        boolean serverChanged = !Objects.equals(old.getImapHost(), a.getImapHost())
                || !Objects.equals(old.getEmail(), a.getEmail());
        if (serverChanged) { // 换了服务器或邮箱，重新从头同步
            a.setLastSyncUid(0L).setUidValidity(0L);
        }
        normalize(a);
        accountMapper.updateById(a);
    }

    public void deleteAccount(Long userId, Long id) {
        validateAccount(userId, id);
        accountMapper.deleteById(id);
        List<Long> messageIds = messageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .eq(ChannelMailMessageDO::getAccountId, id).select(ChannelMailMessageDO::getId))
                .stream().map(ChannelMailMessageDO::getId).toList();
        if (!messageIds.isEmpty()) {
            attachmentMapper.delete(new LambdaQueryWrapperX<ChannelMailAttachmentDO>().in(ChannelMailAttachmentDO::getMessageId, messageIds));
        }
        messageMapper.delete(new LambdaQueryWrapperX<ChannelMailMessageDO>().eq(ChannelMailMessageDO::getAccountId, id));
    }

    public List<ChannelMailAccountRespVO> listAccounts(Long userId) {
        List<ChannelMailAccountDO> list = accountMapper.selectListByUserId(userId);
        List<ChannelMailAccountRespVO> result = BeanUtils.toBean(list, ChannelMailAccountRespVO.class);
        result.forEach(r -> r.setUnrepliedCount(messageMapper.selectUnrepliedCount(List.of(r.getId()))));
        return result;
    }

    public ChannelMailAccountDO validateAccount(Long userId, Long id) {
        ChannelMailAccountDO a = id == null ? null : accountMapper.selectById(id);
        if (a == null || !Objects.equals(a.getUserId(), userId)) {
            throw exception(MAIL_ACCOUNT_NOT_EXISTS);
        }
        return a;
    }

    /** 测试连接；account 可以是尚未保存的表单内容 */
    public void testAccount(Long userId, ChannelMailAccountSaveReqVO req) {
        ChannelMailAccountDO a = BeanUtils.toBean(req, ChannelMailAccountDO.class);
        if (req.getId() != null) {
            ChannelMailAccountDO saved = validateAccount(userId, req.getId());
            if (cn.iocoder.yudao.module.channel.service.oauth.ChannelMailOAuthService.AUTH_OAUTH2.equals(saved.getAuthType())) {
                a = saved; // 授权登录的邮箱按已保存的授权测试
            } else if (StrUtil.isBlank(req.getPassword())) {
                a.setPassword(saved.getPassword());
            }
        }
        normalize(a);
        try {
            MailClient.testConnection(credential(a));
        } catch (Exception e) {
            throw exception(MAIL_ACCOUNT_CONNECT_FAIL, rootMessage(e));
        }
    }

    /** 授权登录的邮箱：用（必要时刷新后的）访问令牌代替密码 */
    private ChannelMailAccountDO credential(ChannelMailAccountDO a) {
        if (!cn.iocoder.yudao.module.channel.service.oauth.ChannelMailOAuthService.AUTH_OAUTH2.equals(a.getAuthType())) {
            return a;
        }
        ChannelMailAccountDO c = BeanUtils.toBean(a, ChannelMailAccountDO.class);
        c.setPassword(channelMailOAuthService.accessToken(a));
        return c;
    }

    private static void normalize(ChannelMailAccountDO a) {
        if (a.getImapSsl() == null) {
            a.setImapSsl(true);
        }
        if (a.getSmtpSsl() == null) {
            a.setSmtpSsl(true);
        }
        if (a.getEmail() != null && StrUtil.isBlank(a.getUsername())) {
            a.setUsername(a.getEmail());
        }
    }

    // ==================== 收信 ====================

    /**
     * 同步一个邮箱的收件箱，返回新入库的邮件数。
     * 调用方需已处于该账号的租户上下文中。
     */
    public int syncAccount(ChannelMailAccountDO account) {
        MailClient.FetchResult fetched;
        try {
            fetched = MailClient.fetchInbox(credential(account));
        } catch (Exception e) {
            String reason = rootMessage(e);
            accountMapper.updateById(new ChannelMailAccountDO().setId(account.getId())
                    .setLastSyncTime(LocalDateTime.now()).setLastSyncError(StrUtil.maxLength(reason, 1000)));
            log.warn("[syncAccount][{}] 收信失败: {}", account.getEmail(), reason);
            throw exception(MAIL_ACCOUNT_SYNC_FAIL, reason);
        }
        Map<String, Long> customerByEmail = new HashMap<>();
        int added = 0;
        for (MailClient.ParsedMail m : fetched.getMails()) {
            if (messageMapper.selectByAccountIdAndImapUid(account.getId(), m.getUid()) != null) {
                continue;
            }
            if (StrUtil.isNotBlank(m.getMessageId())
                    && messageMapper.selectByAccountIdAndMessageId(account.getId(), m.getMessageId()) != null) {
                continue;
            }
            ChannelMailMessageDO msg = ChannelMailMessageDO.builder()
                    .accountId(account.getId())
                    .folder(ChannelMailMessageDO.FOLDER_INBOX)
                    .direction(ChannelMailMessageDO.DIRECTION_IN)
                    .imapUid(m.getUid())
                    .messageId(m.getMessageId())
                    .inReplyTo(m.getInReplyTo())
                    .threadKey(threadKey(account.getId(), m.getMessageId(), m.getInReplyTo(), m.getReferences()))
                    .fromAddress(m.getFromAddress())
                    .fromName(StrUtil.maxLength(m.getFromName(), 250))
                    .toAddresses(String.join(",", m.getTo()))
                    .ccAddresses(String.join(",", m.getCc()))
                    .subject(StrUtil.maxLength(m.getSubject(), 250))
                    .textBody(m.getTextBody())
                    .htmlBody(m.getHtmlBody())
                    .snippet(HtmlText.snippet(m.getTextBody(), m.getHtmlBody(), SNIPPET_LENGTH))
                    .sentTime(m.getSentTime())
                    .readStatus(false)
                    .replied(false)
                    .customerId(customerByEmail.computeIfAbsent(StrUtil.nullToEmpty(m.getFromAddress()), this::findCustomerId))
                    .hasAttachments(m.getAttachments() != null && m.getAttachments().stream().anyMatch(x -> !x.isInlined()))
                    .build();
            messageMapper.insert(msg);
            saveAttachments(msg.getId(), account.getUserId(), m.getAttachments());
            eventPublisher.publishEvent(new ChannelInboundEvent("mail", msg.getId(), msg.getId(), account.getTenantId()));
            added++;
        }
        accountMapper.updateById(new ChannelMailAccountDO().setId(account.getId())
                .setLastSyncTime(LocalDateTime.now()).setLastSyncError("")
                .setLastSyncUid(fetched.getMaxUid()).setUidValidity(fetched.getUidValidity()));
        return added;
    }

    /** 按邮箱地址匹配 CRM 客户（同租户内），找不到返回 null */
    private Long findCustomerId(String email) {
        if (StrUtil.isBlank(email)) {
            return null;
        }
        List<CrmCustomerDO> list = customerMapper.selectList(new LambdaQueryWrapperX<CrmCustomerDO>()
                .eq(CrmCustomerDO::getEmail, email).orderByDesc(CrmCustomerDO::getId).last("LIMIT 1"));
        return list.isEmpty() ? null : list.get(0).getId();
    }

    /** 回复链上已有的邮件用同一个会话标识，否则以本封 Message-ID 开新会话 */
    private String threadKey(Long accountId, String messageId, String inReplyTo, String references) {
        List<String> candidates = new ArrayList<>();
        if (StrUtil.isNotBlank(inReplyTo)) {
            candidates.add(inReplyTo);
        }
        if (StrUtil.isNotBlank(references)) {
            candidates.addAll(Arrays.asList(references.trim().split("\\s+")));
        }
        for (String id : candidates) {
            ChannelMailMessageDO parent = messageMapper.selectByAccountIdAndMessageId(accountId, id);
            if (parent != null && StrUtil.isNotBlank(parent.getThreadKey())) {
                return parent.getThreadKey();
            }
        }
        return StrUtil.blankToDefault(messageId, UUID.randomUUID().toString());
    }

    // ==================== 发信 ====================

    public Long send(Long userId, ChannelMailSendReqVO req) {
        return send(userId, req, false);
    }

    /** @param ai 是否由 AI 数字员工发出（用于标记，以及判断业务员是否在人工跟进） */
    public Long send(Long userId, ChannelMailSendReqVO req, boolean ai) {
        ChannelMailAccountDO account = validateAccount(userId, req.getAccountId());
        List<String> to = cleanAddresses(req.getTo());
        if (to.isEmpty()) {
            throw exception(MAIL_RECIPIENT_EMPTY);
        }
        List<String> cc = cleanAddresses(req.getCc());
        List<String> all = new ArrayList<>(to);
        all.addAll(cc);
        channelSendPolicyService.checkMail(account.getId(), all, ai); // 退订 / 黑名单、发送频率
        ChannelMailMessageDO original = null;
        if (req.getReplyToId() != null) {
            original = getMessage(userId, req.getReplyToId());
        }
        List<ChannelMailAttachmentDO> pending = validatePendingAttachments(userId, req.getAttachmentIds());
        String messageId;
        try {
            List<MailClient.Attachment> files = new ArrayList<>();
            for (ChannelMailAttachmentDO att : pending) {
                files.add(MailClient.Attachment.builder().fileName(att.getFileName()).contentType(att.getContentType())
                        .content(fileHelper.load(att.getFileUrl())).build());
            }
            messageId = MailClient.send(credential(account), to, cc, req.getSubject(), req.getText(), req.getHtml(),
                    original == null ? null : original.getMessageId(), null, files);
        } catch (Exception e) {
            throw exception(MAIL_SEND_FAIL, rootMessage(e));
        }
        ChannelMailMessageDO sent = ChannelMailMessageDO.builder()
                .accountId(account.getId())
                .folder(ChannelMailMessageDO.FOLDER_SENT)
                .direction(ChannelMailMessageDO.DIRECTION_OUT)
                .messageId(messageId)
                .inReplyTo(original == null ? null : original.getMessageId())
                .threadKey(original != null ? original.getThreadKey() : messageId)
                .fromAddress(account.getEmail())
                .fromName(account.getDisplayName())
                .toAddresses(String.join(",", to))
                .ccAddresses(String.join(",", cc))
                .subject(StrUtil.maxLength(req.getSubject(), 250))
                .textBody(StrUtil.isNotBlank(req.getText()) ? req.getText() : HtmlText.toText(req.getHtml()))
                .htmlBody(req.getHtml())
                .snippet(HtmlText.snippet(req.getText(), req.getHtml(), SNIPPET_LENGTH))
                .sentTime(LocalDateTime.now())
                .readStatus(true)
                .replied(false)
                .customerId(original != null ? original.getCustomerId() : findCustomerId(to.get(0)))
                .hasAttachments(!pending.isEmpty())
                .aiGenerated(ai)
                .build();
        messageMapper.insert(sent);
        pending.forEach(att -> attachmentMapper.updateById(new ChannelMailAttachmentDO().setId(att.getId()).setMessageId(sent.getId())));
        if (original != null) {
            messageMapper.updateById(new ChannelMailMessageDO().setId(original.getId()).setReplied(true).setReadStatus(true));
        }
        return sent.getId();
    }

    // ==================== 附件 ====================

    private void saveAttachments(Long messageId, Long userId, List<MailClient.Attachment> list) {
        if (list == null) {
            return;
        }
        for (MailClient.Attachment a : list) {
            try {
                String url = fileHelper.save(a.getContent(), a.getFileName(), "mail", a.getContentType());
                attachmentMapper.insert(ChannelMailAttachmentDO.builder().messageId(messageId).userId(userId)
                        .fileName(StrUtil.maxLength(a.getFileName(), 250)).contentType(a.getContentType())
                        .fileSize((long) a.getContent().length).fileUrl(url).contentId(a.getContentId())
                        .inlined(a.isInlined()).build());
            } catch (Exception e) {
                log.warn("[saveAttachments][邮件 {}] 附件 {} 保存失败", messageId, a.getFileName(), e);
            }
        }
    }

    /** 上传待发送的附件，返回附件信息（含编号，发送时带上） */
    public ChannelMailAttachmentRespVO uploadAttachment(Long userId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw exception(MAIL_ATTACHMENT_EMPTY);
        }
        if (file.getSize() > ChannelFileHelper.MAX_BYTES) {
            throw exception(MAIL_ATTACHMENT_TOO_LARGE);
        }
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "attachment");
        String type = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        String url = fileHelper.save(file.getBytes(), name, "mail", type);
        ChannelMailAttachmentDO att = ChannelMailAttachmentDO.builder().userId(userId).fileName(StrUtil.maxLength(name, 250))
                .contentType(type).fileSize(file.getSize()).fileUrl(url).inlined(false).build();
        attachmentMapper.insert(att);
        return BeanUtils.toBean(att, ChannelMailAttachmentRespVO.class);
    }

    private List<ChannelMailAttachmentDO> validatePendingAttachments(Long userId, List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return List.of();
        }
        List<ChannelMailAttachmentDO> list = attachmentMapper.selectByIds(ids);
        if (list.size() != new HashSet<>(ids).size()
                || list.stream().anyMatch(a -> !Objects.equals(a.getUserId(), userId) || a.getMessageId() != null)) {
            throw exception(MAIL_ATTACHMENT_NOT_EXISTS);
        }
        return list;
    }

    private static List<String> cleanAddresses(List<String> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        return list.stream().filter(StrUtil::isNotBlank).map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct().collect(Collectors.toList());
    }

    // ==================== 查询 ====================

    public PageResult<ChannelMailMessageRespVO> getMessagePage(Long userId, ChannelMailMessagePageReqVO req) {
        Collection<Long> accountIds;
        if (req.getAccountId() != null) {
            accountIds = List.of(validateAccount(userId, req.getAccountId()).getId());
        } else {
            accountIds = accountMapper.selectListByUserId(userId).stream().map(ChannelMailAccountDO::getId).toList();
        }
        if (accountIds.isEmpty()) {
            return PageResult.empty();
        }
        PageResult<ChannelMailMessageDO> page = messageMapper.selectPage(req, accountIds);
        List<ChannelMailMessageRespVO> list = BeanUtils.toBean(page.getList(), ChannelMailMessageRespVO.class);
        list.forEach(r -> r.setTextBody(null).setHtmlBody(null)); // 列表不返回正文
        fillCustomerNames(list);
        return new PageResult<>(list, page.getTotal());
    }

    /** 取邮件详情并标为已读 */
    public ChannelMailMessageRespVO readMessage(Long userId, Long id) {
        ChannelMailMessageDO m = getMessage(userId, id);
        if (!Boolean.TRUE.equals(m.getReadStatus())) {
            messageMapper.updateById(new ChannelMailMessageDO().setId(id).setReadStatus(true));
            m.setReadStatus(true);
        }
        ChannelMailMessageRespVO vo = BeanUtils.toBean(m, ChannelMailMessageRespVO.class);
        List<ChannelMailAttachmentDO> atts = attachmentMapper.selectListByMessageId(id);
        String html = vo.getHtmlBody();
        if (html != null) {
            for (ChannelMailAttachmentDO a : atts) {
                if (StrUtil.isNotBlank(a.getContentId())) {
                    html = html.replace("cid:" + a.getContentId(), a.getFileUrl());
                }
            }
            vo.setHtmlBody(html);
        }
        vo.setAttachments(BeanUtils.toBean(atts.stream().filter(a -> !Boolean.TRUE.equals(a.getInlined())).toList(),
                ChannelMailAttachmentRespVO.class));
        fillCustomerNames(List.of(vo));
        return vo;
    }

    /** 同一会话的全部邮件，按时间正序 */
    public List<ChannelMailMessageRespVO> getThread(Long userId, Long id) {
        ChannelMailMessageDO m = getMessage(userId, id);
        List<ChannelMailMessageDO> list = messageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .eq(ChannelMailMessageDO::getAccountId, m.getAccountId())
                .eq(ChannelMailMessageDO::getThreadKey, m.getThreadKey())
                .orderByAsc(ChannelMailMessageDO::getSentTime));
        return BeanUtils.toBean(list, ChannelMailMessageRespVO.class);
    }

    public void moveToTrash(Long userId, Long id) {
        getMessage(userId, id);
        messageMapper.updateById(new ChannelMailMessageDO().setId(id).setFolder(ChannelMailMessageDO.FOLDER_TRASH));
    }

    public ChannelMailMessageDO getMessage(Long userId, Long id) {
        ChannelMailMessageDO m = messageMapper.selectById(id);
        if (m == null) {
            throw exception(MAIL_MESSAGE_NOT_EXISTS);
        }
        validateAccount(userId, m.getAccountId());
        return m;
    }

    // ==================== 供 AI 使用（调用方需处于正确租户） ====================

    public ChannelMailMessageDO getMessageInternal(Long id) {
        return messageMapper.selectById(id);
    }

    public ChannelMailAccountDO getAccountInternal(Long id) {
        return accountMapper.selectById(id);
    }

    /** 同一会话的邮件，按时间正序 */
    public List<ChannelMailMessageDO> getThreadInternal(ChannelMailMessageDO m) {
        return messageMapper.selectList(new LambdaQueryWrapperX<ChannelMailMessageDO>()
                .eq(ChannelMailMessageDO::getAccountId, m.getAccountId())
                .eq(ChannelMailMessageDO::getThreadKey, m.getThreadKey())
                .orderByAsc(ChannelMailMessageDO::getSentTime));
    }

    private void fillCustomerNames(List<ChannelMailMessageRespVO> list) {
        Set<Long> clueIds = list.stream().map(ChannelMailMessageRespVO::getClueId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!clueIds.isEmpty()) {
            Map<Long, String> names = crmClueMapper.selectByIds(clueIds).stream()
                    .collect(Collectors.toMap(CrmClueDO::getId, CrmClueDO::getName, (a, b) -> a));
            list.forEach(r -> r.setClueName(r.getClueId() == null ? null : names.get(r.getClueId())));
        }
        Set<Long> ids = list.stream().map(ChannelMailMessageRespVO::getCustomerId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        Map<Long, CrmCustomerDO> map = customerMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(CrmCustomerDO::getId, Function.identity()));
        list.forEach(r -> {
            CrmCustomerDO c = r.getCustomerId() == null ? null : map.get(r.getCustomerId());
            if (c != null) {
                r.setCustomerName(c.getName());
            }
        });
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = StrUtil.blankToDefault(t.getMessage(), t.getClass().getSimpleName());
        return msg.length() > 300 ? msg.substring(0, 300) : msg;
    }

}
