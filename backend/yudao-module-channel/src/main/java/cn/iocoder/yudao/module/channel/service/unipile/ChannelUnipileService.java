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

    private static final int SYNC_CHAT_LIMIT = 5;
    private static final int SYNC_MESSAGE_LIMIT = 20;
    /** 托管授权链接有效期（分钟）：手机上登录/选主页比较慢，给足 60 分钟 */
    private static final int AUTH_LINK_MINUTES = 60;
    /** 租户 Key 剩多少天开始换新（每天检查一次） */
    private static final int RENEW_BEFORE_DAYS = 30;
    /** 撞到 Unipile 限流的账号（每账号 100 次/天），本轮跳过它、不影响其它账号 */
    private final java.util.Set<Long> quotaBlockedAccounts = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
            } else if (saved.getKeyExpireTime() == null
                    || !saved.getKeyExpireTime().isAfter(LocalDateTime.now().plusDays(RENEW_BEFORE_DAYS))) {
                // 快到期（或老数据没有到期时间）：先用新 Key 顶上，避免到期当天所有渠道突然失效
                if (renewKey(saved)) {
                    ChannelUnipileCredentialDO renewed = credentialMapper.selectById(saved.getId());
                    if (renewed != null) {
                        return renewed;
                    }
                }
            }
            return saved;
        }
        ChannelUnipileCredentialDO row;
        try {
            String scopeId = UnipileClient.createScope(props, "tenant-" + tenantId, String.valueOf(tenantId));
            UnipileClient.ScopeKey sk = UnipileClient.createScopeKey(props, scopeId, "tenant-" + tenantId, props.getKeyExpireDays());
            row = ChannelUnipileCredentialDO.builder()
                    .scopeId(scopeId).apiKey(sk.key()).apiKeyMask(mask(sk.key())).keyId(sk.id())
                    .keyExpireTime(LocalDateTime.now().plusDays(props.getKeyExpireDays()))
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
            UnipileClient.ScopeKey sk = UnipileClient.createScopeKey(props, scopeId, "tenant-" + tenantId, props.getKeyExpireDays());
            credentialMapper.updateById(ChannelUnipileCredentialDO.builder()
                    .id(saved.getId()).scopeId(scopeId).apiKey(sk.key()).apiKeyMask(mask(sk.key())).keyId(sk.id())
                    .keyExpireTime(LocalDateTime.now().plusDays(props.getKeyExpireDays()))
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
            url = UnipileClient.createAuthLink(props, keyOf(c), redirect, state, AUTH_LINK_MINUTES, p);
        } catch (UnipileClient.UnipileException e) {
            // 把 Unipile 的真实原因抛给前端，别只显示「系统异常」
            String msg = StrUtil.nullToEmpty(e.getMessage());
            if (msg.contains("too_many_requests") || msg.contains("Retry in")) {
                throw new ServiceException(1_030_015_004,
                        "Unipile 请求额度已用完（免费试用每 8 小时 100 次），请稍后再试或升级套餐");
            }
            throw new ServiceException(1_030_015_004, "生成授权链接失败：" + msg);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("provider", p);
        m.put("scopeId", c.getScopeId());
        m.put("expiresInMinutes", AUTH_LINK_MINUTES);
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
        // 全局唯一：同一个渠道账号不能被两家公司同时接入（否则两边都会收到同一个人的消息，还双倍计费）
        java.util.List<ChannelWaAccountDO> sameAccount = TenantUtils.executeIgnore(() ->
                accountMapper.selectList(ChannelWaAccountDO::getPhoneNumberId, accountId));
        for (ChannelWaAccountDO e : sameAccount) {
            if (e.getTenantId() != null && !e.getTenantId().equals(tenantId)) {
                throw new ServiceException(1_030_015_008, "该账号已被其他公司接入，请先在对方后台断开后再试");
            }
        }
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

    /**
     * 找回：把 Unipile 上已经存在、但后台没有记录的账号补进来。
     * 典型场景：客户在托管页面登录成功了，但回调没落库；之后再点绑定会提示 Account already exists。
     */
    @Transactional(rollbackFor = Exception.class)
    public int recoverAccounts(Long userId) {
        ChannelUnipileCredentialDO c = credential();
        if (c == null || StrUtil.isBlank(c.getApiKey())) {
            throw new ServiceException(1_030_015_000, "还没有配置 Unipile 渠道");
        }
        JsonNode r = UnipileClient.listAccounts(props, keyOf(c));
        JsonNode items = r.path("items");
        int n = 0;
        for (JsonNode acc : items) {
            String id = acc.path("id").asText("");
            if (StrUtil.isBlank(id) || accountMapper.selectByPhoneNumberId(id) != null) {
                continue;
            }
            String provider = StrUtil.blankToDefault(acc.path("type").asText(""),
                    acc.path("provider").asText("linkedin")).toLowerCase();
            String name = StrUtil.blankToDefault(acc.path("name").asText(null),
                    acc.path("username").asText(null));
            String rawStatus = acc.path("status").asText("");
            String conn = "running".equalsIgnoreCase(rawStatus)
                    ? ChannelWaAccountDO.CONN_OPEN : ChannelWaAccountDO.CONN_CLOSE;
            accountMapper.insert(ChannelWaAccountDO.builder()
                    .platform(platformOf(provider))
                    .authType(ChannelWaAccountDO.AUTH_UNIPILE)
                    .phoneNumberId(id)
                    .displayPhone(StrUtil.maxLength(name, 64))
                    .userId(userId)
                    .enabled(true)
                    .connState(conn)
                    .build());
            n++;
        }
        log.info("[recoverAccounts][租户 {} 找回 {} 个 Unipile 账号]",
                TenantContextHolder.getTenantId(), n);
        return n;
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

    // ==================== 租户 Key 续期 ====================

    /**
     * 每天检查一次：租户 Key 到期前 30 天就换新。
     * 换新在同一个 Scope 下建新 Key、写回库、再删旧 Key；失败只记日志，下一天再试。
     * 返回成功换新的租户数。
     */
    public int renewExpiringKeys() {
        if (!enabled()) {
            return 0;
        }
        java.util.List<ChannelUnipileCredentialDO> all =
                TenantUtils.executeIgnore(() -> credentialMapper.selectList(null));
        int n = 0;
        for (ChannelUnipileCredentialDO c : all) {
            if (c.getTenantId() == null || "global".equals(c.getScopeId()) || StrUtil.isBlank(c.getScopeId())) {
                continue;
            }
            boolean due = c.getKeyExpireTime() == null
                    || !c.getKeyExpireTime().isAfter(LocalDateTime.now().plusDays(RENEW_BEFORE_DAYS));
            if (!due) {
                continue;
            }
            try {
                Boolean ok = TenantUtils.execute(c.getTenantId(), () -> renewKey(c));
                if (Boolean.TRUE.equals(ok)) {
                    n++;
                }
            } catch (Exception e) {
                log.warn("[renewExpiringKeys][租户 {} 续期失败] {}", c.getTenantId(), e.getMessage());
            }
        }
        if (n > 0) {
            log.info("[renewExpiringKeys][本次换新 {} 个租户的 Unipile Key]", n);
        }
        return n;
    }

    /** 给一个租户的 Key 换新：同 Scope 重新建 Key，成功后删旧 Key */
    private boolean renewKey(ChannelUnipileCredentialDO c) {
        if (StrUtil.isBlank(c.getScopeId()) || "global".equals(c.getScopeId())) {
            return false;
        }
        try {
            UnipileClient.ScopeKey sk = UnipileClient.createScopeKey(props, c.getScopeId(),
                    "tenant-" + c.getTenantId(), props.getKeyExpireDays());
            java.time.LocalDateTime expire = LocalDateTime.now().plusDays(props.getKeyExpireDays());
            credentialMapper.updateById(ChannelUnipileCredentialDO.builder()
                    .id(c.getId()).apiKey(sk.key()).apiKeyMask(mask(sk.key())).keyId(sk.id())
                    .keyExpireTime(expire).status(ChannelUnipileCredentialDO.STATUS_ACTIVE).build());
            if (StrUtil.isNotBlank(c.getKeyId())) {
                try {
                    UnipileClient.deleteScopeKey(props, c.getKeyId());
                } catch (Exception ignore) {
                    // 旧 Key 删不掉就让它自然过期，不影响新 Key
                }
            }
            log.info("[renewKey][租户 {} 的 Unipile Key 已续期到 {}]", c.getTenantId(), expire);
            return true;
        } catch (Exception e) {
            log.warn("[renewKey][租户 {} 换新 Key 失败，继续用旧 Key：{}]", c.getTenantId(), e.getMessage());
            return false;
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
        quotaBlockedAccounts.clear();
        byTenant.forEach((tenantId, list) -> {
            try {
                TenantUtils.execute(tenantId, () -> list.forEach(a -> {
                    // webhook 是主力：最近 6 小时有过消息推送的账号不必再主动拉取，省配额
                    if (a.getLastWebhookTime() != null
                            && a.getLastWebhookTime().isAfter(LocalDateTime.now().minusHours(6))) {
                        return;
                    }
                    if (!quotaBlockedAccounts.contains(a.getId())) {
                        syncOne(a);
                    }
                }));
            } catch (Exception e) {
                log.warn("[sync][租户 {} 补偿同步失败] {}", tenantId, e.getMessage());
            }
        });
    }

    /** 先按通用接口拉会话；LinkedIn 这类 provider 会返回「请用 inbox 接口」，再按收件箱逐个拉 */
    private JsonNode listChatsWithFallback(String key, String accountId, String after) {
        try {
            return UnipileClient.listChats(props, key, accountId, after, SYNC_CHAT_LIMIT);
        } catch (UnipileClient.UnipileException e) {
            JsonNode inboxes = UnipileClient.listInboxes(props, key, accountId);
            var all = JSON.createArrayNode();
            for (JsonNode inbox : inboxes.path("data")) {
                String inboxId = inbox.path("id").asText("");
                if (StrUtil.isBlank(inboxId) || inbox.path("disabled").asBoolean(false)) {
                    continue;
                }
                try {
                    JsonNode page = UnipileClient.listInboxChats(props, key, accountId, inboxId, after, SYNC_CHAT_LIMIT);
                    page.path("data").forEach(all::add);
                } catch (UnipileClient.UnipileException ignore) {
                    // 某个收件箱不支持就跳过，别把整轮同步搞挂
                }
                if (all.size() >= SYNC_CHAT_LIMIT) {
                    break;
                }
            }
            var wrapper = JSON.createObjectNode();
            wrapper.set("data", all);
            return wrapper;
        }
    }

    private void syncOne(ChannelWaAccountDO a) {
        if (StrUtil.isBlank(a.getPhoneNumberId())) {
            return;
        }
        LocalDateTime since = lastSync.getOrDefault(a.getId(), LocalDateTime.now().minusDays(7));
        try {
            String key = keyOf(credential());
            String after = since.atZone(ZoneId.of("UTC")).toInstant().toString();
            JsonNode chats = listChatsWithFallback(key, a.getPhoneNumberId(), after);
            for (JsonNode chat : chats.path("data")) {
                String chatId = chat.path("id").asText("");
                if (StrUtil.isBlank(chatId)) {
                    continue;
                }
                boolean oneToOne = chat.path("is_1to1").asBoolean(false);
                // 已经有这个 chat_id 的会话就沿用它，别再按对方 id 新建一个窗口
                java.util.List<ChannelWaConversationDO> existing =
                        conversationMapper.selectListByAccountIdAndProviderChatId(a.getId(), chatId);
                String waId = !existing.isEmpty() ? existing.get(0).getWaId()
                        : oneToOne ? StrUtil.blankToDefault(chat.path("user_id").asText(null), chatId) : chatId;
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
            String msg = StrUtil.nullToEmpty(e.getMessage());
            if (msg.contains("too_many_requests") || msg.contains("429") || msg.contains("Retry in")) {
                quotaBlockedAccounts.add(a.getId());
                log.warn("[sync][账号 {} 撞到 Unipile 限流，本轮跳过它] {}", a.getId(), msg);
            } else {
                log.debug("[sync][账号 {} 同步跳过：{}]", a.getId(), msg);
            }
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

    /**
     * 把 Unipile 的消息转成现有入库逻辑认识的 Meta 结构。
     *
     * 会话身份统一用 Unipile 的 chat_id 对应到已有窗口：webhook 和补偿同步拿到的对方 id 可能是两种形式
     * （sender_id / chat.user_id），以前各建一条就出现两个同名窗口。现在先按 chat_id 找已有会话，
     * 找到就沿用它的 waId；找不到才按对方 id 新建，并记下 chat_id。
     */
    private void ingest(ChannelWaAccountDO a, JsonNode p) {
        if (!Boolean.TRUE.equals(a.getEnabled())) {
            return;
        }
        String messageId = StrUtil.blankToDefault(p.path("message_id").asText(null), p.path("id").asText(null));
        if (StrUtil.isBlank(messageId)) {
            return;
        }
        String chatId = p.path("chat_id").asText("");
        boolean fromMe = p.path("is_sender").asBoolean(false);
        String senderId = StrUtil.blankToDefault(p.path("sender_id").asText(null), p.path("attendee_id").asText(null));
        String text = StrUtil.blankToDefault(p.path("text").asText(null), p.path("body").asText(""));
        String name = StrUtil.blankToDefault(p.path("sender_name").asText(null), p.path("attendee_name").asText(null));

        String waId = null;
        java.util.List<ChannelWaConversationDO> same = StrUtil.isBlank(chatId) ? java.util.List.of()
                : conversationMapper.selectListByAccountIdAndProviderChatId(a.getId(), chatId);
        if (!same.isEmpty()) {
            waId = same.get(0).getWaId();
            // 历史遗留的重复窗口：把多余那条的 chat_id 清掉，后续消息只进最早那个窗口
            for (int i = 1; i < same.size(); i++) {
                conversationMapper.update(null,
                        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChannelWaConversationDO>()
                                .eq(ChannelWaConversationDO::getId, same.get(i).getId())
                                .set(ChannelWaConversationDO::getProviderChatId, null));
            }
        }
        if (StrUtil.isBlank(waId)) {
            if (fromMe) {
                return; // 自己发出的消息、又找不到对应会话：忽略，别新建窗口
            }
            waId = StrUtil.blankToDefault(senderId, chatId);
        }
        if (StrUtil.isBlank(waId)) {
            return;
        }

        ObjectNode m = JSON.createObjectNode();
        m.put("id", messageId);
        m.put("from", waId);
        m.put("type", StrUtil.isBlank(text) ? "unknown" : "text");
        m.put("timestamp", epochSeconds(p.path("timestamp").asText(null)));
        m.put("text", JSON.createObjectNode().put("body", text));
        if (StrUtil.isNotBlank(name)) {
            m.put("sender_name", name);
        }
        if (fromMe) {
            whatsAppService.evolutionHistory(a, m, waId, true, name);
        } else {
            whatsAppService.evolutionInbound(a, m, name);
        }

        // 记下 Unipile 的 chat_id，回复时要用
        String convKey = a.isWhatsApp() ? ChannelWhatsAppService.waKey(waId) : StrUtil.trimToEmpty(waId);
        ChannelWaConversationDO conv = conversationMapper.selectByAccountIdAndWaId(a.getId(), convKey);
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
