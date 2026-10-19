package cn.iocoder.yudao.module.channel.service.whatsapp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.controller.admin.whatsapp.vo.*;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaMessageDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaMessageMapper;
import cn.iocoder.yudao.module.channel.service.file.ChannelFileHelper;
import org.springframework.web.multipart.MultipartFile;
import cn.iocoder.yudao.module.crm.dal.dataobject.customer.CrmCustomerDO;
import cn.iocoder.yudao.module.crm.dal.mysql.customer.CrmCustomerMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.*;

/**
 * WhatsApp 渠道（Meta 官方 Cloud API）：号码管理、Webhook 收消息与状态、发文本与模板。
 * 号码归属于业务员本人，接口只能访问自己的号码。
 */
@Service
@Slf4j
public class ChannelWhatsAppService {

    /** 客服窗口：客户最后一条消息后 24 小时内可发任意内容 */
    public static final int WINDOW_HOURS = 24;
    private static final List<String> STATUS_ORDER = List.of(
            ChannelWaMessageDO.STATUS_SENT, ChannelWaMessageDO.STATUS_DELIVERED, ChannelWaMessageDO.STATUS_READ);

    @Resource
    private ChannelWaAccountMapper accountMapper;
    @Resource
    private ChannelWaConversationMapper conversationMapper;
    @Resource
    private ChannelWaMessageMapper messageMapper;
    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private ChannelFileHelper fileHelper;

    // ==================== 号码 ====================

    public Long createAccount(Long userId, ChannelWaAccountSaveReqVO req) {
        if (StrUtil.hasBlank(req.getAccessToken(), req.getAppSecret())) {
            throw exception(WA_TOKEN_REQUIRED);
        }
        if (TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(req.getPhoneNumberId().trim())) != null) {
            throw exception(WA_ACCOUNT_EXISTS);
        }
        ChannelWaAccountDO a = BeanUtils.toBean(req, ChannelWaAccountDO.class).setId(null).setUserId(userId)
                .setPhoneNumberId(req.getPhoneNumberId().trim()).setWabaId(req.getWabaId().trim())
                .setEnabled(req.getEnabled() == null || req.getEnabled());
        normalize(a);
        a.setDisplayPhone(fetchDisplayPhone(a));
        accountMapper.insert(a);
        return a.getId();
    }

    public void updateAccount(Long userId, ChannelWaAccountSaveReqVO req) {
        ChannelWaAccountDO old = validateAccount(userId, req.getId());
        ChannelWaAccountDO same = TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(req.getPhoneNumberId().trim()));
        if (same != null && !same.getId().equals(old.getId())) {
            throw exception(WA_ACCOUNT_EXISTS);
        }
        ChannelWaAccountDO a = BeanUtils.toBean(req, ChannelWaAccountDO.class)
                .setPhoneNumberId(req.getPhoneNumberId().trim()).setWabaId(req.getWabaId().trim());
        if (StrUtil.isBlank(req.getAccessToken())) {
            a.setAccessToken(null);
        }
        if (StrUtil.isBlank(req.getAppSecret())) {
            a.setAppSecret(null);
        }
        if (StrUtil.isBlank(req.getVerifyToken())) {
            a.setVerifyToken(null);
        }
        normalize(a);
        ChannelWaAccountDO merged = BeanUtils.toBean(old, ChannelWaAccountDO.class);
        BeanUtil.copyProperties(a, merged, CopyOptions.create().ignoreNullValue());
        a.setDisplayPhone(fetchDisplayPhone(merged));
        accountMapper.updateById(a);
    }

    public void deleteAccount(Long userId, Long id) {
        validateAccount(userId, id);
        accountMapper.deleteById(id);
        conversationMapper.delete(new LambdaQueryWrapperX<ChannelWaConversationDO>().eq(ChannelWaConversationDO::getAccountId, id));
        messageMapper.delete(new LambdaQueryWrapperX<ChannelWaMessageDO>().eq(ChannelWaMessageDO::getAccountId, id));
    }

    public List<ChannelWaAccountRespVO> listAccounts(Long userId) {
        return accountMapper.selectListByUserId(userId).stream().map(a -> {
            ChannelWaAccountRespVO vo = BeanUtils.toBean(a, ChannelWaAccountRespVO.class);
            vo.setWebhookPath("/admin-api/channel/whatsapp/webhook/" + a.getId());
            vo.setHasAccessToken(StrUtil.isNotBlank(a.getAccessToken()));
            vo.setHasAppSecret(StrUtil.isNotBlank(a.getAppSecret()));
            return vo;
        }).toList();
    }

    public ChannelWaAccountDO validateAccount(Long userId, Long id) {
        ChannelWaAccountDO a = id == null ? null : accountMapper.selectById(id);
        if (a == null || !Objects.equals(a.getUserId(), userId)) {
            throw exception(WA_ACCOUNT_NOT_EXISTS);
        }
        return a;
    }

    private static void normalize(ChannelWaAccountDO a) {
        if (a.getApiBase() != null) {
            a.setApiBase(StrUtil.blankToDefault(a.getApiBase().trim(), WaGraphClient.DEFAULT_API_BASE));
        }
        if (a.getApiVersion() != null) {
            a.setApiVersion(StrUtil.blankToDefault(a.getApiVersion().trim(), WaGraphClient.DEFAULT_API_VERSION));
        }
        if (a.getId() == null) {
            a.setApiBase(StrUtil.blankToDefault(a.getApiBase(), WaGraphClient.DEFAULT_API_BASE));
            a.setApiVersion(StrUtil.blankToDefault(a.getApiVersion(), WaGraphClient.DEFAULT_API_VERSION));
            a.setVerifyToken(StrUtil.blankToDefault(a.getVerifyToken(), IdUtil.fastSimpleUUID()));
        }
    }

    /** 用令牌查一次号码信息：既验证配置，又取到展示用号码 */
    private static String fetchDisplayPhone(ChannelWaAccountDO a) {
        try {
            return WaGraphClient.getDisplayPhone(a);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
    }

    public JsonNode listTemplates(Long userId, Long accountId) {
        ChannelWaAccountDO a = validateAccount(userId, accountId);
        try {
            return WaGraphClient.listTemplates(a);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
    }

    // ==================== Webhook ====================

    /** Meta 配置回调地址时的验证：令牌匹配则返回 challenge，否则返回 null */
    public String verifyWebhook(Long accountId, String mode, String token, String challenge) {
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> accountMapper.selectById(accountId));
        if (a == null || !"subscribe".equals(mode) || StrUtil.isBlank(token) || !token.equals(a.getVerifyToken())) {
            return null;
        }
        return challenge;
    }

    /**
     * 处理 Webhook 推送。签名不对返回 false（调用方回 401），其余情况都返回 true，
     * 让 Meta 不再重试；单条消息处理失败只记日志。
     */
    public boolean handleWebhook(Long accountId, byte[] rawBody, String signature) {
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> accountMapper.selectById(accountId));
        if (a == null || !WaGraphClient.verifySignature(a.getAppSecret(), rawBody, signature)) {
            return false;
        }
        if (!Boolean.TRUE.equals(a.getEnabled())) {
            return true;
        }
        JsonNode root = WaGraphClient.parse(rawBody);
        TenantUtils.execute(a.getTenantId(), () -> {
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    String phoneNumberId = value.path("metadata").path("phone_number_id").asText("");
                    if (StrUtil.isNotBlank(phoneNumberId) && !phoneNumberId.equals(a.getPhoneNumberId())) {
                        continue; // 同一应用下其他号码的推送
                    }
                    Map<String, String> names = new HashMap<>();
                    for (JsonNode c : value.path("contacts")) {
                        names.put(c.path("wa_id").asText(), c.path("profile").path("name").asText(null));
                    }
                    for (JsonNode m : value.path("messages")) {
                        try {
                            saveInbound(a, m, names.get(m.path("from").asText()));
                        } catch (Exception e) {
                            log.warn("[handleWebhook][{}] 消息处理失败 {}", a.getId(), m, e);
                        }
                    }
                    for (JsonNode s : value.path("statuses")) {
                        try {
                            applyStatus(a, s);
                        } catch (Exception e) {
                            log.warn("[handleWebhook][{}] 状态处理失败 {}", a.getId(), s, e);
                        }
                    }
                }
            }
            accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setLastWebhookTime(LocalDateTime.now()));
        });
        return true;
    }

    private void saveInbound(ChannelWaAccountDO a, JsonNode m, String profileName) {
        String wamid = m.path("id").asText();
        if (StrUtil.isBlank(wamid) || messageMapper.selectByWaMessageId(a.getId(), wamid) != null) {
            return; // Meta 会重试推送，按 wamid 去重
        }
        String from = digits(m.path("from").asText());
        String type = m.path("type").asText("unknown");
        LocalDateTime time = epoch(m.path("timestamp").asLong(0));
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).direction(ChannelWaMessageDO.DIRECTION_IN)
                .waMessageId(wamid).type(type).status(ChannelWaMessageDO.STATUS_RECEIVED).messageTime(time).build();
        switch (type) {
            case "text" -> msg.setTextBody(m.path("text").path("body").asText());
            case "image", "video", "audio", "document", "sticker" -> {
                JsonNode media = m.path(type);
                msg.setMediaId(media.path("id").asText(null));
                msg.setMimeType(media.path("mime_type").asText(null));
                msg.setFileName(media.path("filename").asText(null));
                msg.setTextBody(media.path("caption").asText(null));
            }
            case "location" -> {
                JsonNode l = m.path("location");
                msg.setTextBody(StrUtil.format("位置：{} {}（{}, {}）", l.path("name").asText(""),
                        l.path("address").asText(""), l.path("latitude").asText(), l.path("longitude").asText()).trim());
            }
            case "button" -> msg.setTextBody(m.path("button").path("text").asText());
            case "interactive" -> {
                JsonNode i = m.path("interactive");
                JsonNode reply = i.has("button_reply") ? i.path("button_reply") : i.path("list_reply");
                msg.setTextBody(reply.path("title").asText(null));
            }
            case "reaction" -> msg.setTextBody("回应了表情 " + m.path("reaction").path("emoji").asText(""));
            default -> msg.setTextBody("[暂不支持的消息类型：" + type + "]");
        }
        ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), from);
        if (conv == null) {
            conv = ChannelWaConversationDO.builder().accountId(a.getId()).waId(from).profileName(profileName)
                    .customerId(findCustomerId(from)).unreadCount(0).build();
            conversationMapper.insert(conv);
        }
        msg.setConversationId(conv.getId());
        messageMapper.insert(msg);
        ChannelWaConversationDO upd = new ChannelWaConversationDO().setId(conv.getId())
                .setLastInboundTime(max(conv.getLastInboundTime(), time))
                .setLastMessageTime(max(conv.getLastMessageTime(), time))
                .setLastMessagePreview(preview(msg))
                .setUnreadCount((conv.getUnreadCount() == null ? 0 : conv.getUnreadCount()) + 1);
        if (StrUtil.isNotBlank(profileName)) {
            upd.setProfileName(profileName);
        }
        if (conv.getCustomerId() == null) {
            upd.setCustomerId(findCustomerId(from));
        }
        conversationMapper.updateById(upd);
    }

    /** 发出消息的状态只前进不后退；failed 记录原因 */
    private void applyStatus(ChannelWaAccountDO a, JsonNode s) {
        ChannelWaMessageDO msg = messageMapper.selectByWaMessageId(a.getId(), s.path("id").asText());
        if (msg == null) {
            return;
        }
        String status = s.path("status").asText();
        ChannelWaMessageDO upd = new ChannelWaMessageDO().setId(msg.getId());
        if (ChannelWaMessageDO.STATUS_FAILED.equals(status)) {
            JsonNode err = s.path("errors").path(0);
            upd.setStatus(status).setErrorMessage(StrUtil.maxLength(StrUtil.blankToDefault(
                    err.path("error_data").path("details").asText(null), err.path("title").asText("发送失败")), 500));
        } else if (STATUS_ORDER.indexOf(status) > STATUS_ORDER.indexOf(msg.getStatus())) {
            upd.setStatus(status);
        } else {
            return;
        }
        messageMapper.updateById(upd);
    }

    // ==================== 发送 ====================

    public ChannelWaMessageRespVO send(Long userId, ChannelWaSendReqVO req) {
        ChannelWaConversationDO conv;
        ChannelWaAccountDO a;
        if (req.getConversationId() != null) {
            conv = validateConversation(userId, req.getConversationId());
            a = accountMapper.selectById(conv.getAccountId());
        } else {
            a = validateAccount(userId, req.getAccountId());
            String to = digits(req.getTo());
            if (to.length() < 8 || to.length() > 15) {
                throw exception(WA_PHONE_INVALID);
            }
            conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), to);
            if (conv == null) {
                conv = ChannelWaConversationDO.builder().accountId(a.getId()).waId(to)
                        .customerId(findCustomerId(to)).unreadCount(0).build();
                conversationMapper.insert(conv);
            }
        }
        boolean template = StrUtil.isNotBlank(req.getTemplateName());
        if (!template && StrUtil.isBlank(req.getText())) {
            throw exception(WA_CONTENT_EMPTY);
        }
        if (!template && !windowOpen(conv)) {
            throw exception(WA_WINDOW_CLOSED);
        }
        String wamid;
        try {
            wamid = template
                    ? WaGraphClient.sendTemplate(a, conv.getWaId(), req.getTemplateName(),
                            StrUtil.blankToDefault(req.getTemplateLanguage(), "en_US"), req.getTemplateParams())
                    : WaGraphClient.sendText(a, conv.getWaId(), req.getText());
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_OUT)
                .waMessageId(wamid).type(template ? "template" : "text")
                .textBody(template ? StrUtil.blankToDefault(req.getTemplatePreview(), "[模板] " + req.getTemplateName()) : req.getText())
                .templateName(template ? req.getTemplateName() : null)
                .status(ChannelWaMessageDO.STATUS_SENT).messageTime(now).build();
        messageMapper.insert(msg);
        conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId())
                .setLastMessageTime(now).setLastMessagePreview(preview(msg)));
        return BeanUtils.toBean(msg, ChannelWaMessageRespVO.class);
    }

    // ==================== 媒体 ====================

    /** WhatsApp 对上传文件的大小限制：图片 5MB，音视频与文件 16MB（文件实际可到 100MB，这里统一从严） */
    private static final int IMAGE_LIMIT = 5 * 1024 * 1024;
    private static final int OTHER_LIMIT = 16 * 1024 * 1024;

    /** 取媒体文件地址：首次查看时从 Meta 下载并保存，之后直接返回 */
    public ChannelWaMessageRespVO getMedia(Long userId, Long messageId) {
        ChannelWaMessageDO m = messageMapper.selectById(messageId);
        if (m == null) {
            throw exception(WA_MESSAGE_NOT_EXISTS);
        }
        ChannelWaAccountDO a = validateAccount(userId, m.getAccountId());
        if (StrUtil.isBlank(m.getFileUrl())) {
            if (StrUtil.isBlank(m.getMediaId())) {
                throw exception(WA_NO_MEDIA);
            }
            try {
                WaGraphClient.MediaInfo info = WaGraphClient.getMedia(a, m.getMediaId());
                byte[] bytes = WaGraphClient.download(a, info.url(), ChannelFileHelper.MAX_BYTES);
                String mime = StrUtil.blankToDefault(m.getMimeType(), info.mimeType());
                String name = StrUtil.blankToDefault(m.getFileName(), m.getType() + "-" + m.getId() + extension(mime));
                String url = fileHelper.save(bytes, name, "whatsapp", mime);
                messageMapper.updateById(new ChannelWaMessageDO().setId(m.getId()).setFileUrl(url)
                        .setFileSize((long) bytes.length).setMimeType(mime));
                m.setFileUrl(url).setFileSize((long) bytes.length).setMimeType(mime);
            } catch (WaGraphClient.WaApiException e) {
                throw exception(WA_API_FAIL, e.getMessage());
            }
        }
        return BeanUtils.toBean(m, ChannelWaMessageRespVO.class);
    }

    /** 发送图片、视频、语音或文件（24 小时窗口内） */
    public ChannelWaMessageRespVO sendMedia(Long userId, Long conversationId, MultipartFile file, String caption) throws Exception {
        ChannelWaConversationDO conv = validateConversation(userId, conversationId);
        ChannelWaAccountDO a = accountMapper.selectById(conv.getAccountId());
        if (!windowOpen(conv)) {
            throw exception(WA_WINDOW_CLOSED);
        }
        if (file == null || file.isEmpty()) {
            throw exception(WA_CONTENT_EMPTY);
        }
        String mime = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        String kind = WaGraphClient.mediaKind(mime);
        if (file.getSize() > ("image".equals(kind) ? IMAGE_LIMIT : OTHER_LIMIT)) {
            throw exception(WA_FILE_TOO_LARGE);
        }
        if ("document".equals(kind) && mime.startsWith("image/")) {
            mime = "application/octet-stream"; // 非 JPEG/PNG 的图片按文件发送
        }
        byte[] bytes = file.getBytes();
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "file" + extension(mime));
        String wamid;
        try {
            String mediaId = WaGraphClient.upload(a, bytes, name, mime);
            wamid = WaGraphClient.sendMedia(a, conv.getWaId(), kind, mediaId, caption, name);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
        String url = fileHelper.save(bytes, name, "whatsapp", file.getContentType());
        LocalDateTime now = LocalDateTime.now();
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_OUT)
                .waMessageId(wamid).type(kind).textBody(StrUtil.emptyToNull(caption)).mimeType(file.getContentType())
                .fileName(name).fileUrl(url).fileSize((long) bytes.length)
                .status(ChannelWaMessageDO.STATUS_SENT).messageTime(now).build();
        messageMapper.insert(msg);
        conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId())
                .setLastMessageTime(now).setLastMessagePreview(preview(msg)));
        return BeanUtils.toBean(msg, ChannelWaMessageRespVO.class);
    }

    private static String extension(String mime) {
        return switch (StrUtil.nullToEmpty(mime).toLowerCase()) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "audio/ogg", "audio/ogg; codecs=opus" -> ".ogg";
            case "audio/mpeg" -> ".mp3";
            case "application/pdf" -> ".pdf";
            default -> "";
        };
    }

    // ==================== 查询 ====================

    public PageResult<ChannelWaConversationRespVO> getConversationPage(Long userId, ChannelWaConversationPageReqVO req) {
        Collection<Long> accountIds = req.getAccountId() != null
                ? List.of(validateAccount(userId, req.getAccountId()).getId())
                : accountMapper.selectListByUserId(userId).stream().map(ChannelWaAccountDO::getId).toList();
        if (accountIds.isEmpty()) {
            return PageResult.empty();
        }
        PageResult<ChannelWaConversationDO> page = conversationMapper.selectPage(req, accountIds, req.getKeyword(), req.getUnreadOnly());
        return new PageResult<>(toConversationVOs(page.getList()), page.getTotal());
    }

    /** 会话消息（按时间正序），并清零未读 */
    public List<ChannelWaMessageRespVO> getMessages(Long userId, Long conversationId, Long beforeId, Integer limit) {
        ChannelWaConversationDO conv = validateConversation(userId, conversationId);
        if (conv.getUnreadCount() != null && conv.getUnreadCount() > 0 && beforeId == null) {
            conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId()).setUnreadCount(0));
        }
        int n = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        return BeanUtils.toBean(messageMapper.selectRecent(conv.getId(), beforeId, n), ChannelWaMessageRespVO.class);
    }

    public ChannelWaConversationRespVO getConversation(Long userId, Long id) {
        return toConversationVOs(List.of(validateConversation(userId, id))).get(0);
    }

    private ChannelWaConversationDO validateConversation(Long userId, Long id) {
        ChannelWaConversationDO c = id == null ? null : conversationMapper.selectById(id);
        if (c == null) {
            throw exception(WA_CONVERSATION_NOT_EXISTS);
        }
        validateAccount(userId, c.getAccountId());
        return c;
    }

    private List<ChannelWaConversationRespVO> toConversationVOs(List<ChannelWaConversationDO> list) {
        Set<Long> ids = list.stream().map(ChannelWaConversationDO::getCustomerId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CrmCustomerDO> customers = CollUtil.isEmpty(ids) ? Map.of()
                : customerMapper.selectByIds(ids).stream().collect(Collectors.toMap(CrmCustomerDO::getId, Function.identity()));
        return list.stream().map(c -> {
            ChannelWaConversationRespVO vo = BeanUtils.toBean(c, ChannelWaConversationRespVO.class);
            CrmCustomerDO cu = c.getCustomerId() == null ? null : customers.get(c.getCustomerId());
            vo.setCustomerName(cu == null ? null : cu.getName());
            vo.setWindowOpen(windowOpen(c));
            vo.setWindowExpireTime(c.getLastInboundTime() == null ? null : c.getLastInboundTime().plusHours(WINDOW_HOURS));
            return vo;
        }).toList();
    }

    // ==================== 工具 ====================

    private static boolean windowOpen(ChannelWaConversationDO c) {
        return c.getLastInboundTime() != null
                && c.getLastInboundTime().plusHours(WINDOW_HOURS).isAfter(LocalDateTime.now());
    }

    /** 按 WhatsApp 号码匹配 CRM 客户：比较去掉符号后的纯数字 */
    private Long findCustomerId(String waId) {
        if (StrUtil.length(waId) < 8) {
            return null;
        }
        String tail = waId.substring(waId.length() - 8);
        List<CrmCustomerDO> list = customerMapper.selectList(new LambdaQueryWrapperX<CrmCustomerDO>()
                .and(w -> w.like(CrmCustomerDO::getWhatsapp, tail).or().like(CrmCustomerDO::getMobile, tail))
                .orderByDesc(CrmCustomerDO::getId).last("LIMIT 20"));
        return list.stream()
                .filter(c -> waId.equals(digits(c.getWhatsapp())) || waId.equals(digits(c.getMobile())))
                .map(CrmCustomerDO::getId).findFirst().orElse(null);
    }

    static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D", "");
    }

    private static LocalDateTime epoch(long seconds) {
        return seconds <= 0 ? LocalDateTime.now() : LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a == null || (b != null && b.isAfter(a)) ? b : a;
    }

    private static String preview(ChannelWaMessageDO m) {
        String t = StrUtil.isNotBlank(m.getTextBody()) ? m.getTextBody() : "[" + m.getType() + "]";
        return StrUtil.maxLength(t.replaceAll("\\s+", " "), 120);
    }

}
