package cn.iocoder.yudao.module.channel.service.unipile;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.unipile.ChannelUnipileAuthStateDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.unipile.ChannelUnipileCredentialDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
import cn.iocoder.yudao.module.channel.dal.mysql.unipile.ChannelUnipileAuthStateMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.unipile.ChannelUnipileCredentialMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaConversationMapper;
import cn.iocoder.yudao.module.channel.framework.unipile.ChannelUnipileProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unipile 接入（替换 Evolution）：托管扫码、收消息、发消息。
 *
 * 隔离：每个公司一条 {@link ChannelUnipileCredentialDO}（Scope + 专属 Key），
 * 所有对 Unipile 的读写都用这家公司自己的 Key，用别家的 Key 在 Unipile 侧会被拒绝。
 */
@Service
@Slf4j
public class ChannelUnipileService {

    public static final String WEBHOOK_PATH = "/admin-api/channel/unipile/webhook";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private ChannelUnipileProperties props;
    @Resource
    private ChannelUnipileCredentialMapper credentialMapper;
    @Resource
    private ChannelUnipileAuthStateMapper authStateMapper;
    @Resource
    private ChannelWaAccountMapper accountMapper;
    @Resource
    private ChannelWaConversationMapper conversationMapper;
    @Resource
    private ChannelWhatsAppService whatsAppService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService channelNotifyService;

    /** 补偿同步：每个账号上次同步到的时间（只放内存，重启后从 15 分钟前重新对账） */
    private final java.util.Map<Long, LocalDateTime> lastSync = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int SYNC_CHAT_LIMIT = 20;
    private static final int SYNC_MESSAGE_LIMIT = 20;

    public boolean enabled() {
        return props.ready();
    }

    // ==================== 凭证（Scope + 专属 Key） ====================

    /** 当前公司的凭证；没有就现场建 Scope 与 Key（每个公司一个隔离边界） */
    @Transactional(rollbackFor = Exception.class)
    public ChannelUnipileCredentialDO credential() {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ChannelUnipileCredentialDO saved = credentialMapper.selectCurrent();
        if (saved != null && StrUtil.isNotBlank(saved.getApiKey())) {
            // 之前用的是 Account Key（建不了 Scope），只能降级成单 Key；换成 Service Key 后自动升级成一户一 Scope
            if ("global".equals(saved.getScopeId())) {
                ChannelUnipileCredentialDO upgraded = tryUpgradeToScoped(tenantId, saved);
                if (upgraded != null) {
                    return upgraded;
                }
            }
            return saved;
        }
        ChannelUnipileCredentialDO row;
        try {
            String scopeId = UnipileClient.createScope(props, "tenant-" + tenantId, String.valueOf(tenantId));
            String apiKey = UnipileClient.createScopeKey(props, scopeId, "tenant-" + tenantId, props.getKeyExpireDays());
            row = ChannelUnipileCredentialDO.builder()
                    .scopeId(scopeId).apiKey(apiKey).apiKeyMask(mask(apiKey))
                    .status(ChannelUnipileCredentialDO.STATUS_ACTIVE).build();
            log.info("[credential][租户 {} 创建 Unipile Scope {}]", tenantId, scopeId);
        } catch (UnipileClient.UnipileException e) {
            if (!denied(e)) {
                throw e;
            }
            // 后台建的是 Account Key（全局），没有建 Scope / 建 Key 的权限：降级为单 Key 模式。
            // 这时隔离靠应用层：account_id 全局唯一映射租户 + 每次调用校验账号归属（不会串户，但强度弱一层）。
            log.warn("[credential][租户 {} 无法建 Scope（{}），改用平台全局 Key]", tenantId, e.getMessage());
            row = ChannelUnipileCredentialDO.builder()
                    .scopeId("global").apiKey(props.getServiceKey()).apiKeyMask(mask(props.getServiceKey()))
                    .status(ChannelUnipileCredentialDO.STATUS_ACTIVE).build();
        }
        credentialMapper.insert(row);
        return row;
    }

    /** 单 Key 模式升级成一户一 Scope；失败返回 null（继续用单 Key） */
    private ChannelUnipileCredentialDO tryUpgradeToScoped(Long tenantId, ChannelUnipileCredentialDO saved) {
        try {
            String scopeId = UnipileClient.createScope(props, "tenant-" + tenantId, String.valueOf(tenantId));
            String apiKey = UnipileClient.createScopeKey(props, scopeId, "tenant-" + tenantId, props.getKeyExpireDays());
            credentialMapper.updateById(ChannelUnipileCredentialDO.builder()
                    .id(saved.getId()).scopeId(scopeId).apiKey(apiKey).apiKeyMask(mask(apiKey))
                    .status(ChannelUnipileCredentialDO.STATUS_ACTIVE).build());
            log.info("[credential][租户 {} 从单 Key 升级到 Scope {}]", tenantId, scopeId);
            return credentialMapper.selectById(saved.getId());
        } catch (Exception e) {
            log.warn("[credential][租户 {} 升级 Scope 失败，继续用单 Key：{}]", tenantId, e.getMessage());
            return null;
        }
    }

    /** 是不是「没有权限建 Scope」这类错误（Account Key 常见） */
    private static boolean denied(Throwable e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        return m.contains("HTTP 401") || m.contains("HTTP 403");
    }

    /** 当前公司凭证的脱敏信息（给前端展示） */
    public Map<String, Object> credentialInfo() {
        ChannelUnipileCredentialDO c = credentialMapper.selectCurrent();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", enabled());
        m.put("scopeId", c == null ? null : c.getScopeId());
        // 脱敏值每次按实际使用的 Key 现算，不依赖库里的历史值
        m.put("apiKeyMask", enabled() ? mask(keyOf(c)) : null);
        m.put("status", c == null ? null : c.getStatus());
        return m;
    }

    // ==================== 连接（托管授权） ====================

    /** 支持的渠道（Unipile 的 providers 名称） */
    public static final java.util.List<String> SUPPORTED_PROVIDERS =
            java.util.List.of("whatsapp", "linkedin", "instagram", "facebook", "telegram");

    /** Unipile 的 provider → 我们库里的 platform */
    public static String platformOf(String provider) {
        String p = StrUtil.blankToDefault(provider, "whatsapp").trim().toLowerCase();
        return switch (p) {
            case "linkedin" -> ChannelWaAccountDO.PLATFORM_LINKEDIN;
            case "instagram" -> ChannelWaAccountDO.PLATFORM_INSTAGRAM;
            case "facebook", "messenger" -> ChannelWaAccountDO.PLATFORM_MESSENGER;
            case "telegram" -> ChannelWaAccountDO.PLATFORM_TELEGRAM;
            default -> ChannelWaAccountDO.PLATFORM_WHATSAPP;
        };
    }

    /** 生成托管授权链接：前端打开后客户扫码 / 登录授权 */
    public Map<String, Object> connect(String provider) {
        String p = StrUtil.blankToDefault(provider, "whatsapp").trim().toLowerCase();
        if (!SUPPORTED_PROVIDERS.contains(p)) {
            throw new ServiceException(1_030_015_005, "不支持的渠道：" + provider);
        }
        ChannelUnipileCredentialDO c = credential();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        if (userId == null) {
            throw new ServiceException(1_030_015_006, "请先登录后再连接渠道");
        }
        String redirect = StrUtil.blankToDefault(props.getPublicBase(), "") + props.getCallbackPath();
        // 一次性令牌：回调时用它换回「哪个租户、哪个成员、哪个渠道」，避免伪造 state 把号绑到别人名下
        String state = createAuthState(tenantId, userId, p);
        String url;
        try {
            url = UnipileClient.createAuthLink(props, keyOf(c), redirect, state, 15, p);
        } catch (UnipileClient.UnipileException e) {
            // 把 Unipile 的真实原因抛给前端，别只显示「系统异常」
            throw new ServiceException(1_030_015_004, "生成授权链接失败：" + e.getMessage());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("provider", p);
        m.put("scopeId", c.getScopeId());
        m.put("expiresInMinutes", 15);
        return m;
    }

    /** 生成一次性授权令牌：记录「哪个成员、哪个渠道」，回调时换回来 */
    private String createAuthState(Long tenantId, Long userId, String provider) {
        String token = IdUtil.fastSimpleUUID();
        authStateMapper.insert(ChannelUnipileAuthStateDO.builder()
                .token(token).tenantId(tenantId).userId(userId).provider(provider)
                .expireTime(LocalDateTime.now().plusMinutes(15)).used(false).build());
        return token;
    }

    /**
     * 回调时用 token 换回发起信息；无效 / 过期 / 已用返回 null。
     * 一次性使用：换到就立刻标记已用，防止重放。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChannelUnipileAuthStateDO consumeAuthState(String token) {
        if (StrUtil.isBlank(token)) {
            return null;
        }
        ChannelUnipileAuthStateDO state = TenantUtils.executeIgnore(() ->
                authStateMapper.selectOne(ChannelUnipileAuthStateDO::getToken, token));
        if (state == null || Boolean.TRUE.equals(state.getUsed())) {
            return null;
        }
        if (state.getExpireTime() == null || state.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        TenantUtils.executeIgnore(() -> {
            authStateMapper.updateById(ChannelUnipileAuthStateDO.builder().id(state.getId()).used(true).build());
        });
        return state;
    }

    /** 授权完成后的回调：用该租户 Key 校验账号确实属于它，再落库 */
    @Transactional(rollbackFor = Exception.class)
    public ChannelWaAccountDO handleCallback(Long tenantId, Long userId, String provider, String accountId) {
        if (tenantId == null || StrUtil.isBlank(accountId)) {
            throw new ServiceException(1_030_015_000, "回调缺少租户或账号编号");
        }
        if (userId == null) {
            throw new ServiceException(1_030_015_006, "授权记录缺少发起人，请回到后台重新发起");
        }
        String platform = platformOf(provider);
        return TenantUtils.execute(tenantId, () -> {
            ChannelUnipileCredentialDO c = credential();
            JsonNode acc = UnipileClient.getAccount(props, keyOf(c), accountId);
            String name = StrUtil.blankToDefault(acc.path("name").asText(null), acc.path("username").asText(null));
            // 回调时按 Unipile 的真实状态落库：credentials 登录可能刚连上就被 FB 踢下线
            String rawStatus = acc.path("status").asText("");
            String conn = "running".equalsIgnoreCase(rawStatus) ? ChannelWaAccountDO.CONN_OPEN : ChannelWaAccountDO.CONN_CLOSE;
            ChannelWaAccountDO a = accountMapper.selectByPhoneNumberId(accountId);
            if (a == null) {
                a = ChannelWaAccountDO.builder()
                        .platform(platform)
                        .authType(ChannelWaAccountDO.AUTH_UNIPILE)
                        .phoneNumberId(accountId)
                        .displayPhone(name)
                        .userId(userId)
                        .enabled(true)
                        .connState(conn)
                        .build();
                accountMapper.insert(a);
            } else {
                accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId())
                        .setPlatform(platform).setDisplayPhone(name)
                        .setConnState(conn).setEnabled(true));
            }
            return a;
        });
    }

    /** 断开并删除该号码（会同时断开客户手机上的关联设备） */
    @Transactional(rollbackFor = Exception.class)
    public void disconnect(Long accountId) {
        ChannelWaAccountDO a = accountMapper.selectById(accountId);
        if (a == null) {
            throw new ServiceException(1_030_015_001, "号码不存在");
        }
        if (!a.isUnipile()) {
            throw new ServiceException(1_030_015_002, "这个号码不是 Unipile 接入的");
        }
        String key = tenantKey(a.getTenantId());
        try {
            UnipileClient.deleteAccount(props, key, a.getPhoneNumberId());
        } catch (Exception e) {
            log.warn("[disconnect][账号 {} 删除失败] {}", accountId, e.getMessage());
        }
        whatsAppService.deleteAccountInternal(accountId);
    }

    // ==================== 发送 ====================

    public String sendText(ChannelWaAccountDO a, String chatId, String text) {
        if (StrUtil.isBlank(chatId)) {
            throw new ServiceException(1_030_015_003, "这个会话还没有同步到 Unipile 的会话编号，暂时不能回复");
        }
        String key = tenantKey(a.getTenantId());
        try {
            return UnipileClient.sendText(props, key, a.getPhoneNumberId(), chatId, text);
        } catch (UnipileClient.UnipileException e) {
            // 把 Unipile 的真实原因返回给前端（会话已失效、对方不是好友、账号掉线等）
            throw new ServiceException(1_030_015_007, "发送失败：" + e.getMessage());
        }
    }

    // ==================== 补偿同步 ====================

    /**
     * webhook 是主力，这里兜底：拉最近有更新的会话，把缺的消息补进库。
     * 走 {@code evolutionHistory}：按消息编号去重、不计未读、不触发 AI 回复。
     */
    public void syncRecentMessages() {
        java.util.List<ChannelWaAccountDO> accounts = TenantUtils.executeIgnore(() ->
                accountMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChannelWaAccountDO>()
                        .eq(ChannelWaAccountDO::getAuthType, ChannelWaAccountDO.AUTH_UNIPILE)
                        .eq(ChannelWaAccountDO::getEnabled, true)));
        if (accounts.isEmpty()) {
            return;
        }
        java.util.Map<Long, java.util.List<ChannelWaAccountDO>> byTenant = accounts.stream()
                .filter(a -> a.getTenantId() != null)
                .collect(java.util.stream.Collectors.groupingBy(ChannelWaAccountDO::getTenantId));
        byTenant.forEach((tenantId, list) -> {
            try {
                TenantUtils.execute(tenantId, () -> list.forEach(this::syncOne));
            } catch (Exception e) {
                log.warn("[sync][租户 {} 补偿同步失败] {}", tenantId, e.getMessage());
            }
        });
    }

    private void syncOne(ChannelWaAccountDO a) {
        if (StrUtil.isBlank(a.getPhoneNumberId())) {
            return;
        }
        LocalDateTime since = lastSync.getOrDefault(a.getId(), LocalDateTime.now().minusMinutes(15));
        try {
            String key = keyOf(credential());
            String after = since.atZone(ZoneId.of("UTC")).toInstant().toString();
            JsonNode chats = UnipileClient.listChats(props, key, a.getPhoneNumberId(), after, SYNC_CHAT_LIMIT);
            for (JsonNode chat : chats.path("data")) {
                String chatId = chat.path("id").asText("");
                if (StrUtil.isBlank(chatId)) {
                    continue;
                }
                boolean oneToOne = chat.path("is_1to1").asBoolean(false);
                String waId = oneToOne
                        ? StrUtil.blankToDefault(chat.path("user_id").asText(null), chatId) : chatId;
                String chatName = chat.path("name").asText(null);
                JsonNode msgs;
                try {
                    msgs = UnipileClient.listMessages(props, key, a.getPhoneNumberId(), chatId, SYNC_MESSAGE_LIMIT);
                } catch (UnipileClient.UnipileException e) {
                    continue;
                }
                for (JsonNode msg : msgs.path("data")) {
                    String id = msg.path("id").asText("");
                    if (StrUtil.isBlank(id)) {
                        continue;
                    }
                    String text = msg.path("text").asText("");
                    ObjectNode m = JSON.createObjectNode();
                    m.put("id", id);
                    m.put("from", StrUtil.blankToDefault(msg.path("sender_id").asText(null), waId));
                    m.put("type", StrUtil.isBlank(text) ? "unknown" : "text");
                    m.put("timestamp", epochSeconds(msg.path("timestamp").asText(null)));
                    m.put("text", JSON.createObjectNode().put("body", text));
                    whatsAppService.evolutionHistory(a, m, waId, msg.path("is_sender").asBoolean(false), chatName);
                }
                // 老会话没记 Unipile 的 chat_id 时补上，回复要用
                String convKey = a.isWhatsApp() ? ChannelWhatsAppService.waKey(waId) : StrUtil.trimToEmpty(waId);
                ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), convKey);
                if (conv != null && StrUtil.isBlank(conv.getProviderChatId())) {
                    conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId())
                            .setProviderChatId(chatId));
                }
            }
            lastSync.put(a.getId(), LocalDateTime.now());
        } catch (UnipileClient.UnipileException e) {
            log.debug("[sync][账号 {} 同步跳过：{}]", a.getId(), e.getMessage());
        }
    }

    // ==================== Webhook ====================

    /** 校验签名并分发事件；签名不对返回 false（调用方回 401） */
    public boolean handleWebhook(byte[] raw, String signature) {
        if (!UnipileClient.verifySignature(props.getWebhookSecret(), signature, raw)) {
            log.warn("[handleWebhook][Unipile 签名校验失败]");
            return false;
        }
        JsonNode event;
        try {
            event = JSON.readTree(raw);
        } catch (Exception e) {
            log.warn("[handleWebhook][解析失败] {}", e.getMessage());
            return true;
        }
        String type = event.path("type").asText("");
        JsonNode payload = event.path("payload");
        String accountId = StrUtil.blankToDefault(payload.path("account_id").asText(null), event.path("account_id").asText(null));
        if (StrUtil.isBlank(accountId)) {
            return true;
        }
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(accountId));
        if (a == null) {
            log.info("[handleWebhook][账号 {} 还没绑定，先忽略]", accountId);
            return true;
        }
        TenantUtils.execute(a.getTenantId(), () -> dispatch(a, type, payload));
        return true;
    }

    private void dispatch(ChannelWaAccountDO a, String type, JsonNode payload) {
        // Unipile v2 的状态事件是 account.status.running / .disconnected / .errored 这种带后缀的形式
        if (type.startsWith("account.status")) {
            String status = type.startsWith("account.status.")
                    ? type.substring("account.status.".length())
                    : payload.path("status").asText("");
            String conn = ("running".equalsIgnoreCase(status) || "OK".equalsIgnoreCase(status))
                    ? ChannelWaAccountDO.CONN_OPEN : ChannelWaAccountDO.CONN_CLOSE;
            if (!conn.equals(a.getConnState())) {
                accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setConnState(conn));
                log.info("[dispatch][账号 {} 状态变为 {}（事件 {}）]", a.getId(), conn, type);
                // 从「已连接」掉到「断开」时通知负责人重新授权，否则客户会以为还在收消息
                if (ChannelWaAccountDO.CONN_CLOSE.equals(conn)
                        && ChannelWaAccountDO.CONN_OPEN.equals(a.getConnState())) {
                    channelNotifyService.onAccountOffline(a);
                }
            }
            return;
        }
        switch (type) {
            case "message.new" -> ingest(a, payload);
            default -> log.debug("[dispatch][忽略事件 {}]", type);
        }
    }

    /** 把 Unipile 的消息转成现有入库逻辑认识的 Meta 结构，复用会话/未读/AI 回复 */
    private void ingest(ChannelWaAccountDO a, JsonNode p) {
        if (!Boolean.TRUE.equals(a.getEnabled())) {
            return;
        }
        String messageId = StrUtil.blankToDefault(p.path("message_id").asText(null), p.path("id").asText(null));
        if (StrUtil.isBlank(messageId)) {
            return;
        }
        String chatId = p.path("chat_id").asText("");
        String senderId = StrUtil.blankToDefault(p.path("sender_id").asText(null), p.path("attendee_id").asText(null));
        if (StrUtil.isBlank(senderId)) {
            senderId = chatId;
        }
        String text = StrUtil.blankToDefault(p.path("text").asText(null), p.path("body").asText(""));
        String name = StrUtil.blankToDefault(p.path("sender_name").asText(null), p.path("attendee_name").asText(null));

        ObjectNode m = JSON.createObjectNode();
        m.put("id", messageId);
        m.put("from", senderId);
        m.put("type", StrUtil.isBlank(text) ? "unknown" : "text");
        m.put("timestamp", epochSeconds(p.path("timestamp").asText(null)));
        m.put("text", JSON.createObjectNode().put("body", text));
        if (StrUtil.isNotBlank(name)) {
            m.put("sender_name", name);
        }
        whatsAppService.evolutionInbound(a, m, name);

        // 记下 Unipile 的 chat_id，回复时要用
        String waId = senderId;
        ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), waId);
        if (conv != null && StrUtil.isBlank(conv.getProviderChatId()) && StrUtil.isNotBlank(chatId)) {
            conversationMapper.updateById(new ChannelWaConversationDO().setId(conv.getId()).setProviderChatId(chatId));
        }
    }

    private static long epochSeconds(String iso) {
        if (StrUtil.isBlank(iso)) {
            return Instant.now().getEpochSecond();
        }
        try {
            return Instant.parse(iso).getEpochSecond();
        } catch (Exception ignore) {
            try {
                return LocalDateTime.parse(iso.replace("Z", "")).atZone(ZoneId.systemDefault()).toEpochSecond();
            } catch (Exception e) {
                return Instant.now().getEpochSecond();
            }
        }
    }

    private String tenantKey(Long tenantId) {
        return TenantUtils.execute(tenantId, () -> keyOf(credential()));
    }

    private static String mask(String key) {
        if (StrUtil.isBlank(key)) {
            return "";
        }
        // 只留最后 4 位；注意 subSuf(key, n) 是「从第 n 位截到结尾」，不是「取最后 n 位」
        String tail = StrUtil.subSuf(key, Math.max(0, key.length() - 4));
        return StrUtil.maxLength("pk_****" + tail, 250);
    }

    /**
     * 实际用来调 Unipile 的 Key。
     * 单 Key 模式（scopeId=global）直接用配置里的平台 Key，避免数据库里的加密值来回转换出问题。
     */
    private String keyOf(ChannelUnipileCredentialDO c) {
        if (c == null) {
            return props.getServiceKey();
        }
        if ("global".equals(c.getScopeId()) || StrUtil.isBlank(c.getApiKey())) {
            return props.getServiceKey();
        }
        return c.getApiKey();
    }
}
