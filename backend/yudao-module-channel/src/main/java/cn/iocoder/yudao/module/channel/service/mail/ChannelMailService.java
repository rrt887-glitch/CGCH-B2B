package cn.iocoder.yudao.module.channel.service.mail;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.channel.controller.admin.mail.vo.*;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailMessageMapper;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
    private ChannelMailAccountMapper accountMapper;
    @Resource
    private ChannelMailMessageMapper messageMapper;
    @Resource
    private CrmCustomerMapper customerMapper;

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
        if (StrUtil.isBlank(req.getPassword()) && req.getId() != null) {
            a.setPassword(validateAccount(userId, req.getId()).getPassword());
        }
        normalize(a);
        try {
            MailClient.testConnection(a);
        } catch (Exception e) {
            throw exception(MAIL_ACCOUNT_CONNECT_FAIL, rootMessage(e));
        }
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
            fetched = MailClient.fetchInbox(account);
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
                    .build();
            messageMapper.insert(msg);
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
        ChannelMailAccountDO account = validateAccount(userId, req.getAccountId());
        List<String> to = cleanAddresses(req.getTo());
        if (to.isEmpty()) {
            throw exception(MAIL_RECIPIENT_EMPTY);
        }
        List<String> cc = cleanAddresses(req.getCc());
        ChannelMailMessageDO original = null;
        if (req.getReplyToId() != null) {
            original = getMessage(userId, req.getReplyToId());
        }
        String messageId;
        try {
            messageId = MailClient.send(account, to, cc, req.getSubject(), req.getText(), req.getHtml(),
                    original == null ? null : original.getMessageId(), null);
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
                .build();
        messageMapper.insert(sent);
        if (original != null) {
            messageMapper.updateById(new ChannelMailMessageDO().setId(original.getId()).setReplied(true).setReadStatus(true));
        }
        return sent.getId();
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

    private void fillCustomerNames(List<ChannelMailMessageRespVO> list) {
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
