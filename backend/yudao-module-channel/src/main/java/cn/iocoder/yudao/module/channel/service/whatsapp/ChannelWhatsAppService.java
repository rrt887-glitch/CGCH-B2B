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
import cn.iocoder.yudao.module.crm.dal.dataobject.clue.CrmClueDO;
import cn.iocoder.yudao.module.crm.dal.mysql.clue.CrmClueMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import cn.iocoder.yudao.module.channel.service.event.ChannelInboundEvent;
import org.springframework.context.ApplicationEventPublisher;
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
 * Meta 即时通讯渠道：WhatsApp（官方 Cloud API）、Facebook 主页私信（Messenger）、Instagram 私信。
 * 三个平台共用账号、会话、消息表（以 platform 区分），AI 接待、线索建档、看板等能力因此直接复用。
 * 账号归属于业务员本人，接口只能访问自己的账号。
 */
@Service
@Slf4j
public class ChannelWhatsAppService {

    /** 客服窗口：客户最后一条消息后 24 小时内可发任意内容 */
    public static final int WINDOW_HOURS = 24;
    private static final List<String> STATUS_ORDER = List.of(
            ChannelWaMessageDO.STATUS_SENT, ChannelWaMessageDO.STATUS_DELIVERED, ChannelWaMessageDO.STATUS_READ);

    @Resource
    private cn.iocoder.yudao.module.channel.service.policy.ChannelSendPolicyService channelSendPolicyService;
    @Resource
    private ChannelWaAccountMapper accountMapper;
    @Resource
    private ChannelWaConversationMapper conversationMapper;
    @Resource
    private ChannelWaMessageMapper messageMapper;
    @Resource
    private CrmCustomerMapper customerMapper;
    @Resource
    private CrmClueMapper crmClueMapper;
    @Resource
    private ChannelFileHelper fileHelper;
    @Resource
    private ApplicationEventPublisher eventPublisher;
    @Resource
    private cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties channelOAuthProperties;

    private static final Set<String> PLATFORMS = Set.of(ChannelWaAccountDO.PLATFORM_WHATSAPP,
            ChannelWaAccountDO.PLATFORM_MESSENGER, ChannelWaAccountDO.PLATFORM_INSTAGRAM);

    private static String platformOf(String p) {
        String v = StrUtil.blankToDefault(StrUtil.trim(p), ChannelWaAccountDO.PLATFORM_WHATSAPP);
        if (!PLATFORMS.contains(v)) {
            throw exception(META_PLATFORM_INVALID, v);
        }
        return v;
    }

    private static String platformLabel(ChannelWaAccountDO a) {
        if (a.isWhatsApp()) {
            return "WhatsApp";
        }
        return ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(a.getPlatform()) ? "Instagram" : "Facebook Messenger";
    }

    // ==================== 号码 ====================

    public Long createAccount(Long userId, ChannelWaAccountSaveReqVO req) {
        if (StrUtil.hasBlank(req.getAccessToken(), req.getAppSecret())) {
            throw exception(WA_TOKEN_REQUIRED);
        }
        if (TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(req.getPhoneNumberId().trim())) != null) {
            throw exception(WA_ACCOUNT_EXISTS);
        }
        String platform = platformOf(req.getPlatform());
        if (ChannelWaAccountDO.PLATFORM_WHATSAPP.equals(platform) && StrUtil.isBlank(req.getWabaId())) {
            throw exception(WA_WABA_REQUIRED);
        }
        ChannelWaAccountDO a = BeanUtils.toBean(req, ChannelWaAccountDO.class).setId(null).setUserId(userId)
                .setPlatform(platform).setAuthType(ChannelWaAccountDO.AUTH_MANUAL)
                .setPhoneNumberId(req.getPhoneNumberId().trim()).setWabaId(StrUtil.trimToNull(req.getWabaId()))
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
        if (old.isWhatsApp() && StrUtil.isBlank(req.getWabaId())) {
            throw exception(WA_WABA_REQUIRED);
        }
        ChannelWaAccountDO a = BeanUtils.toBean(req, ChannelWaAccountDO.class)
                .setPlatform(null).setAuthType(null) // 平台和接入方式创建后不能改
                .setPhoneNumberId(req.getPhoneNumberId().trim()).setWabaId(StrUtil.trimToNull(req.getWabaId()));
        if (StrUtil.isBlank(req.getAccessToken())) {
            a.setAccessToken(null);
        }
        if (StrUtil.isBlank(req.getAppSecret())) {
            a.setAppSecret(null);
        }
        if (a.getWabaId() == null) {
            a.setWabaId(null);
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

    /** 删除账号及其会话、消息（不校验归属，供 Meta 数据删除回调使用） */
    public void deleteAccountInternal(Long id) {
        accountMapper.deleteById(id);
        conversationMapper.delete(new LambdaQueryWrapperX<ChannelWaConversationDO>().eq(ChannelWaConversationDO::getAccountId, id));
        messageMapper.delete(new LambdaQueryWrapperX<ChannelWaMessageDO>().eq(ChannelWaMessageDO::getAccountId, id));
    }

    public List<ChannelWaAccountRespVO> listAccounts(Long userId) {
        return accountMapper.selectListByUserId(userId).stream().map(a -> {
            ChannelWaAccountRespVO vo = BeanUtils.toBean(a, ChannelWaAccountRespVO.class);
            vo.setPlatform(StrUtil.blankToDefault(a.getPlatform(), ChannelWaAccountDO.PLATFORM_WHATSAPP));
            vo.setAuthType(StrUtil.blankToDefault(a.getAuthType(), ChannelWaAccountDO.AUTH_MANUAL));
            vo.setWebhookPath(ChannelWaAccountDO.AUTH_OAUTH.equals(a.getAuthType())
                    ? "/admin-api/channel/meta/webhook" : "/admin-api/channel/whatsapp/webhook/" + a.getId());
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

    /** 用令牌查一次账号信息：既验证配置，又取到展示用号码 / 主页名称 / Instagram 用户名 */
    private static String fetchDisplayPhone(ChannelWaAccountDO a) {
        try {
            return a.isWhatsApp() ? WaGraphClient.getDisplayPhone(a) : WaGraphClient.getMessagingProfile(a);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
    }

    public JsonNode listTemplates(Long userId, Long accountId) {
        ChannelWaAccountDO a = validateAccount(userId, accountId);
        if (!a.isWhatsApp()) {
            throw exception(META_TEMPLATE_UNSUPPORTED, platformLabel(a));
        }
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
        if (a == null || !WaGraphClient.verifySignature(appSecret(a), rawBody, signature)) {
            return false;
        }
        if (!Boolean.TRUE.equals(a.getEnabled())) {
            return true;
        }
        JsonNode root = WaGraphClient.parse(rawBody);
        if (!a.isWhatsApp()) {
            for (JsonNode entry : root.path("entry")) {
                if (a.getPhoneNumberId().equals(entry.path("id").asText())) {
                    handleMessagingEntry(a, entry);
                }
            }
            return true;
        }
        TenantUtils.execute(a.getTenantId(), () -> {
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    JsonNode value = change.path("value");
                    String phoneNumberId = value.path("metadata").path("phone_number_id").asText("");
                    if (StrUtil.isNotBlank(phoneNumberId) && !phoneNumberId.equals(a.getPhoneNumberId())) {
                        continue; // 同一应用下其他号码的推送
                    }
                    processWaChange(a, value);
                }
            }
            accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setLastWebhookTime(LocalDateTime.now()));
        });
        return true;
    }

    /** 处理一条 WhatsApp 推送（messages / statuses），调用方已切换到账号所属租户 */
    private void processWaChange(ChannelWaAccountDO a, JsonNode value) {
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

    /** 手动接入的账号用自己填写的应用密钥；Facebook 登录授权的账号用服务器配置的 Meta 应用密钥 */
    private String appSecret(ChannelWaAccountDO a) {
        return StrUtil.blankToDefault(a.getAppSecret(), channelOAuthProperties.getMeta().getClientSecret());
    }

    /** 应用级 Webhook（Facebook 登录授权的主页与 Instagram 共用一个回调地址）的验证 */
    public String verifyAppWebhook(String mode, String token, String challenge) {
        String expected = channelOAuthProperties.getMeta().getVerifyToken();
        if (!"subscribe".equals(mode) || StrUtil.isBlank(expected) || !expected.equals(token)) {
            return null;
        }
        return challenge;
    }

    /**
     * 应用级 Webhook：按 entry.id（主页编号或 Instagram 账号编号）找到账号再处理。
     * 签名用服务器配置的 Meta 应用密钥校验，不对返回 false。
     */
    public boolean handleAppWebhook(byte[] rawBody, String signature) {
        if (!WaGraphClient.verifySignature(channelOAuthProperties.getMeta().getClientSecret(), rawBody, signature)) {
            return false;
        }
        JsonNode root = WaGraphClient.parse(rawBody);
        if ("whatsapp_business_account".equals(root.path("object").asText())) {
            handleAppWhatsApp(root);
            return true;
        }
        String platform = "instagram".equals(root.path("object").asText()) ? ChannelWaAccountDO.PLATFORM_INSTAGRAM
                : "page".equals(root.path("object").asText()) ? ChannelWaAccountDO.PLATFORM_MESSENGER : null;
        if (platform == null) {
            return true;
        }
        for (JsonNode entry : root.path("entry")) {
            String id = entry.path("id").asText("");
            ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(id));
            if (a == null || !platform.equals(a.getPlatform()) || !Boolean.TRUE.equals(a.getEnabled())) {
                continue;
            }
            handleMessagingEntry(a, entry);
        }
        return true;
    }

    /** 应用级 Webhook 中的 WhatsApp 推送（客户通过嵌入式注册开通的号码）：按 phone_number_id 找账号 */
    private void handleAppWhatsApp(JsonNode root) {
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                String pnid = value.path("metadata").path("phone_number_id").asText("");
                ChannelWaAccountDO a = StrUtil.isBlank(pnid) ? null
                        : TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(pnid));
                if (a == null || !a.isWhatsApp() || !Boolean.TRUE.equals(a.getEnabled())) {
                    continue;
                }
                TenantUtils.execute(a.getTenantId(), () -> {
                    processWaChange(a, value);
                    accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setLastWebhookTime(LocalDateTime.now()));
                });
            }
        }
    }

    // ==================== Messenger / Instagram 推送 ====================

    private void handleMessagingEntry(ChannelWaAccountDO a, JsonNode entry) {
        TenantUtils.execute(a.getTenantId(), () -> {
            for (JsonNode ev : entry.path("messaging")) {
                try {
                    handleMessagingEvent(a, ev);
                } catch (Exception e) {
                    log.warn("[handleMessagingEntry][{}] 事件处理失败 {}", a.getId(), ev, e);
                }
            }
            accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setLastWebhookTime(LocalDateTime.now()));
        });
    }

    private void handleMessagingEvent(ChannelWaAccountDO a, JsonNode ev) {
        String sender = ev.path("sender").path("id").asText("");
        String recipient = ev.path("recipient").path("id").asText("");
        LocalDateTime time = epochMillis(ev.path("timestamp").asLong(0));
        if (ev.has("delivery")) {
            for (JsonNode mid : ev.path("delivery").path("mids")) {
                ChannelWaMessageDO m = messageMapper.selectByWaMessageId(a.getId(), mid.asText());
                if (m != null && ChannelWaMessageDO.STATUS_SENT.equals(m.getStatus())) {
                    messageMapper.updateById(new ChannelWaMessageDO().setId(m.getId()).setStatus(ChannelWaMessageDO.STATUS_DELIVERED));
                }
            }
            return;
        }
        if (ev.has("read")) {
            ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), sender);
            if (conv == null) {
                return;
            }
            JsonNode read = ev.path("read");
            LocalDateTime watermark = read.has("watermark") ? epochMillis(read.path("watermark").asLong()) : null;
            if (read.has("mid")) { // Instagram 回执带的是消息编号
                ChannelWaMessageDO m = messageMapper.selectByWaMessageId(a.getId(), read.path("mid").asText());
                watermark = m == null ? null : m.getMessageTime();
            }
            if (watermark != null) {
                messageMapper.markReadBefore(conv.getId(), watermark);
            }
            return;
        }
        JsonNode message = ev.path("message");
        String text;
        String mid;
        if (ev.has("postback")) { // 点了按钮
            JsonNode pb = ev.path("postback");
            mid = pb.path("mid").asText("pb-" + sender + "-" + ev.path("timestamp").asText());
            text = StrUtil.blankToDefault(pb.path("title").asText(null), pb.path("payload").asText(""));
            saveMessagingInbound(a, sender, mid, "button", text, null, time);
            return;
        }
        if (message.isMissingNode() || message.path("is_deleted").asBoolean(false)) {
            return; // 已撤回、表情回应等不需要处理
        }
        mid = message.path("mid").asText("");
        if (StrUtil.isBlank(mid)) {
            return;
        }
        text = message.path("text").asText(null);
        if (message.path("is_echo").asBoolean(false)) {
            // 回显：主页一方发出的消息。系统自己发出的已入库；在 Meta 后台或手机上人工回复的补记为发出消息
            if (ECHO.equals(message.path("metadata").asText(null)) || messageMapper.selectByWaMessageId(a.getId(), mid) != null) {
                return;
            }
            saveMessagingOutbound(a, recipient, mid, text, message.path("attachments"), time);
            return;
        }
        JsonNode atts = message.path("attachments");
        if (atts.isArray() && !atts.isEmpty()) {
            for (int i = 0; i < atts.size(); i++) {
                JsonNode att = atts.get(i);
                String type = attachmentType(att.path("type").asText("file"));
                String url = att.path("payload").path("url").asText(null);
                String body = i == 0 ? text : null;
                if (body == null && !"image".equals(type) && !"video".equals(type) && !"audio".equals(type) && !"document".equals(type)) {
                    body = attachmentText(att);
                }
                saveMessagingInbound(a, sender, i == 0 ? mid : mid + "#" + i, type, body, url, time);
            }
            return;
        }
        saveMessagingInbound(a, sender, mid, "text", StrUtil.blankToDefault(text, "[暂不支持的消息类型]"), null, time);
    }

    private static final String ECHO = WaGraphClient.ECHO_METADATA;

    private static String attachmentType(String t) {
        return switch (t) {
            case "image", "video", "audio" -> t;
            case "file" -> "document";
            case "location" -> "location";
            default -> t; // fallback（分享链接）、story_mention、share、template、ig_reel 等
        };
    }

    private static String attachmentText(JsonNode att) {
        String t = att.path("type").asText("");
        JsonNode p = att.path("payload");
        return switch (t) {
            case "location" -> StrUtil.format("位置：{}, {}", p.path("coordinates").path("lat").asText(),
                    p.path("coordinates").path("long").asText());
            case "fallback", "share", "ig_reel", "reel" -> "分享：" + StrUtil.blankToDefault(att.path("title").asText(null),
                    StrUtil.blankToDefault(p.path("title").asText(null), p.path("url").asText("")));
            case "story_mention" -> "在快拍中提到了你";
            default -> "[" + t + "]";
        };
    }

    private ChannelWaConversationDO messagingConversation(ChannelWaAccountDO a, String userId) {
        ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), userId);
        if (conv == null) {
            conv = ChannelWaConversationDO.builder().accountId(a.getId()).platform(a.getPlatform()).waId(userId)
                    .profileName(WaGraphClient.getMessagingUserName(a, userId)).unreadCount(0).build();
            conversationMapper.insert(conv);
        }
        return conv;
    }

    private void saveMessagingInbound(ChannelWaAccountDO a, String sender, String mid, String type, String text,
                                      String mediaUrl, LocalDateTime time) {
        if (StrUtil.isBlank(sender) || messageMapper.selectByWaMessageId(a.getId(), mid) != null) {
            return; // Meta 会重试推送，按 mid 去重
        }
        ChannelWaConversationDO conv = messagingConversation(a, sender);
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_IN)
                .waMessageId(mid).type(type).textBody(text).mediaId(mediaUrl)
                .status(ChannelWaMessageDO.STATUS_RECEIVED).messageTime(time).build();
        messageMapper.insert(msg);
        ChannelWaConversationDO upd = new ChannelWaConversationDO().setId(conv.getId())
                .setLastInboundTime(max(conv.getLastInboundTime(), time))
                .setLastMessageTime(max(conv.getLastMessageTime(), time))
                .setLastMessagePreview(preview(msg))
                .setUnreadCount((conv.getUnreadCount() == null ? 0 : conv.getUnreadCount()) + 1);
        if (StrUtil.isBlank(conv.getProfileName())) {
            upd.setProfileName(WaGraphClient.getMessagingUserName(a, sender));
        }
        conversationMapper.updateById(upd);
        if (mid.indexOf('#') < 0) { // 同一条消息的多个附件只触发一次
            eventPublisher.publishEvent(new ChannelInboundEvent("whatsapp", conv.getId(), msg.getId(), a.getTenantId()));
        }
    }

    private void saveMessagingOutbound(ChannelWaAccountDO a, String userId, String mid, String text, JsonNode atts, LocalDateTime time) {
        if (StrUtil.isBlank(userId)) {
            return;
        }
        ChannelWaConversationDO conv = messagingConversation(a, userId);
        String type = "text";
        String mediaUrl = null;
        if (atts.isArray() && !atts.isEmpty()) {
            type = attachmentType(atts.get(0).path("type").asText("file"));
            mediaUrl = atts.get(0).path("payload").path("url").asText(null);
        }
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_OUT)
                .waMessageId(mid).type(type).textBody(text).mediaId(mediaUrl)
                .status(ChannelWaMessageDO.STATUS_SENT).messageTime(time).aiGenerated(false).build();
        messageMapper.insert(msg);
        conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId())
                .setLastMessageTime(max(conv.getLastMessageTime(), time)).setLastMessagePreview(preview(msg)));
    }

    private static LocalDateTime epochMillis(long ms) {
        return ms <= 0 ? LocalDateTime.now() : LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
    }

    // ==================== WhatsApp 推送 ====================

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
            conv = ChannelWaConversationDO.builder().accountId(a.getId()).platform(ChannelWaAccountDO.PLATFORM_WHATSAPP)
                    .waId(from).profileName(profileName)
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
        if (!"reaction".equals(type)) { // 表情回应不需要回复
            eventPublisher.publishEvent(new ChannelInboundEvent("whatsapp", conv.getId(), msg.getId(), a.getTenantId()));
        }
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
        return send(userId, req, false);
    }

    /** @param ai 是否由 AI 数字员工发出 */
    public ChannelWaMessageRespVO send(Long userId, ChannelWaSendReqVO req, boolean ai) {
        ChannelWaConversationDO conv;
        ChannelWaAccountDO a;
        if (req.getConversationId() != null) {
            conv = validateConversation(userId, req.getConversationId());
            a = accountMapper.selectById(conv.getAccountId());
        } else {
            a = validateAccount(userId, req.getAccountId());
            if (!a.isWhatsApp()) {
                throw exception(META_NEW_CONVERSATION_UNSUPPORTED, platformLabel(a));
            }
            String to = digits(req.getTo());
            if (to.length() < 8 || to.length() > 15) {
                throw exception(WA_PHONE_INVALID);
            }
            conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), to);
            if (conv == null) {
                conv = ChannelWaConversationDO.builder().accountId(a.getId()).platform(ChannelWaAccountDO.PLATFORM_WHATSAPP)
                        .waId(to).customerId(findCustomerId(to)).unreadCount(0).build();
                conversationMapper.insert(conv);
            }
        }
        boolean template = StrUtil.isNotBlank(req.getTemplateName());
        if (template && !a.isWhatsApp()) {
            throw exception(META_TEMPLATE_UNSUPPORTED, platformLabel(a));
        }
        if (!template && StrUtil.isBlank(req.getText())) {
            throw exception(WA_CONTENT_EMPTY);
        }
        if (!template && !windowOpen(conv)) {
            throw exception(WA_WINDOW_CLOSED);
        }
        channelSendPolicyService.checkWhatsApp(a.getId(), conv.getWaId(), ai); // 退订 / 黑名单、发送频率
        String wamid;
        try {
            wamid = template
                    ? WaGraphClient.sendTemplate(a, conv.getWaId(), req.getTemplateName(),
                            StrUtil.blankToDefault(req.getTemplateLanguage(), "en_US"), req.getTemplateParams())
                    : a.isWhatsApp() ? WaGraphClient.sendText(a, conv.getWaId(), req.getText())
                    : WaGraphClient.sendMessagingText(a, conv.getWaId(), req.getText());
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_OUT)
                .waMessageId(wamid).type(template ? "template" : "text")
                .textBody(template ? StrUtil.blankToDefault(req.getTemplatePreview(), "[模板] " + req.getTemplateName()) : req.getText())
                .templateName(template ? req.getTemplateName() : null)
                .status(ChannelWaMessageDO.STATUS_SENT).messageTime(now).aiGenerated(ai).build();
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
                // Messenger / Instagram 推送里直接带文件地址；WhatsApp 需要先按媒体编号查地址
                WaGraphClient.MediaInfo info = m.getMediaId().startsWith("http")
                        ? new WaGraphClient.MediaInfo(m.getMediaId(), null) : WaGraphClient.getMedia(a, m.getMediaId());
                byte[] bytes = WaGraphClient.download(a, info.url(), ChannelFileHelper.MAX_BYTES);
                String mime = StrUtil.blankToDefault(StrUtil.blankToDefault(m.getMimeType(), info.mimeType()), guessMime(m.getType()));
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
        if (!a.isWhatsApp()) {
            return sendMessagingMedia(userId, a, conv, file, caption);
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
        // 先存本地：文件服务会按内容校验真实类型，不通过就不发给客户
        String url = fileHelper.save(bytes, name, "whatsapp", file.getContentType());
        String wamid;
        try {
            String mediaId = WaGraphClient.upload(a, bytes, name, mime);
            wamid = WaGraphClient.sendMedia(a, conv.getWaId(), kind, mediaId, caption, name);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
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

    /** Messenger：先发附件，再把说明文字作为单独一条文本发出（附件消息不支持说明文字） */
    private ChannelWaMessageRespVO sendMessagingMedia(Long userId, ChannelWaAccountDO a, ChannelWaConversationDO conv,
                                                      MultipartFile file, String caption) throws Exception {
        if (ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(a.getPlatform())) {
            throw exception(META_MEDIA_UNSUPPORTED);
        }
        if (file.getSize() > MESSENGER_LIMIT) {
            throw exception(WA_FILE_TOO_LARGE);
        }
        channelSendPolicyService.checkWhatsApp(a.getId(), conv.getWaId(), false);
        String mime = StrUtil.blankToDefault(file.getContentType(), "application/octet-stream");
        String kind = WaGraphClient.messagingKind(mime);
        byte[] bytes = file.getBytes();
        String name = StrUtil.blankToDefault(file.getOriginalFilename(), "file" + extension(mime));
        String url = fileHelper.save(bytes, name, "whatsapp", file.getContentType());
        String mid;
        try {
            mid = WaGraphClient.sendMessagingAttachment(a, conv.getWaId(), kind, bytes, name, mime);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
        LocalDateTime now = LocalDateTime.now();
        ChannelWaMessageDO msg = ChannelWaMessageDO.builder()
                .accountId(a.getId()).conversationId(conv.getId()).direction(ChannelWaMessageDO.DIRECTION_OUT)
                .waMessageId(mid).type("file".equals(kind) ? "document" : kind).mimeType(file.getContentType())
                .fileName(name).fileUrl(url).fileSize((long) bytes.length)
                .status(ChannelWaMessageDO.STATUS_SENT).messageTime(now).build();
        messageMapper.insert(msg);
        conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId())
                .setLastMessageTime(now).setLastMessagePreview(preview(msg)));
        if (StrUtil.isNotBlank(caption)) {
            send(userId, new ChannelWaSendReqVO().setConversationId(conv.getId()).setText(caption));
        }
        return BeanUtils.toBean(msg, ChannelWaMessageRespVO.class);
    }

    /** Messenger 附件上限 25MB */
    private static final int MESSENGER_LIMIT = 25 * 1024 * 1024;

    private static String guessMime(String type) {
        return switch (StrUtil.nullToEmpty(type)) {
            case "image" -> "image/jpeg";
            case "video" -> "video/mp4";
            case "audio" -> "audio/mpeg";
            default -> "application/octet-stream";
        };
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
        Set<Long> clueIds = list.stream().map(ChannelWaConversationDO::getClueId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> clueNames = clueIds.isEmpty() ? Map.of()
                : crmClueMapper.selectByIds(clueIds).stream().collect(Collectors.toMap(CrmClueDO::getId, CrmClueDO::getName, (a, b) -> a));
        return list.stream().map(c -> {
            ChannelWaConversationRespVO vo = BeanUtils.toBean(c, ChannelWaConversationRespVO.class);
            vo.setPlatform(StrUtil.blankToDefault(c.getPlatform(), ChannelWaAccountDO.PLATFORM_WHATSAPP));
            CrmCustomerDO cu = c.getCustomerId() == null ? null : customers.get(c.getCustomerId());
            vo.setCustomerName(cu == null ? null : cu.getName());
            vo.setClueName(c.getClueId() == null ? null : clueNames.get(c.getClueId()));
            vo.setWindowOpen(windowOpen(c));
            vo.setWindowExpireTime(c.getLastInboundTime() == null ? null : c.getLastInboundTime().plusHours(WINDOW_HOURS));
            return vo;
        }).toList();
    }

    // ==================== 供 AI 使用 ====================

    public void updateIntentInternal(Long id, String intent) {
        conversationMapper.updateById(new ChannelWaConversationDO().setId(id).setIntent(intent));
    }

    public ChannelWaConversationDO getConversationInternal(Long id) {
        return conversationMapper.selectById(id);
    }

    public ChannelWaAccountDO getAccountInternal(Long id) {
        return accountMapper.selectById(id);
    }

    public List<ChannelWaMessageDO> getRecentMessagesInternal(Long conversationId, int limit) {
        return messageMapper.selectRecent(conversationId, null, limit);
    }

    public boolean isWindowOpen(ChannelWaConversationDO c) {
        return windowOpen(c);
    }

    // ==================== 工具 ====================

    private static boolean windowOpen(ChannelWaConversationDO c) {
        return c.getLastInboundTime() != null
                && c.getLastInboundTime().plusHours(WINDOW_HOURS).isAfter(LocalDateTime.now());
    }

    /**
     * 按 WhatsApp 号码匹配 CRM 客户：比较去掉空格、横线、括号等符号后的纯数字。
     * 业务员录入的号码常带分隔符，所以数据库里只按最后 4 位粗筛，再在内存里精确比对。
     */
    private Long findCustomerId(String waId) {
        if (StrUtil.length(waId) < 8) {
            return null;
        }
        String tail = waId.substring(waId.length() - 4);
        List<CrmCustomerDO> list = customerMapper.selectList(new LambdaQueryWrapperX<CrmCustomerDO>()
                .and(w -> w.likeLeft(CrmCustomerDO::getWhatsapp, tail).or().likeLeft(CrmCustomerDO::getMobile, tail))
                .orderByDesc(CrmCustomerDO::getId).last("LIMIT 200"));
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
