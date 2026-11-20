package cn.iocoder.yudao.module.channel.service.unipile;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.unipile.ChannelUnipileCredentialDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaConversationDO;
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
    private ChannelWaAccountMapper accountMapper;
    @Resource
    private ChannelWaConversationMapper conversationMapper;
    @Resource
    private ChannelWhatsAppService whatsAppService;

    public boolean enabled() {
        return props.ready();
    }

    // ==================== 凭证（Scope + 专属 Key） ====================

    /** 当前公司的凭证；没有就现场建 Scope 与 Key（每个公司一个隔离边界） */
    @Transactional(rollbackFor = Exception.class)
    public ChannelUnipileCredentialDO credential() {
        ChannelUnipileCredentialDO saved = credentialMapper.selectCurrent();
        if (saved != null && StrUtil.isNotBlank(saved.getApiKey())) {
            return saved;
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
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
        m.put("apiKeyMask", c == null ? null : c.getApiKeyMask());
        m.put("status", c == null ? null : c.getStatus());
        return m;
    }

    // ==================== 连接（托管扫码） ====================

    /** 生成托管授权链接：前端打开后客户扫码或输配对码 */
    public Map<String, Object> connect() {
        ChannelUnipileCredentialDO c = credential();
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String redirect = StrUtil.blankToDefault(props.getPublicBase(), "") + props.getCallbackPath();
        String url = UnipileClient.createAuthLink(props, c.getApiKey(), redirect, String.valueOf(tenantId), 15);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("url", url);
        m.put("scopeId", c.getScopeId());
        m.put("expiresInMinutes", 15);
        return m;
    }

    /** 扫码完成后的回调：用该租户 Key 校验账号确实属于它，再落库 */
    @Transactional(rollbackFor = Exception.class)
    public ChannelWaAccountDO handleCallback(Long tenantId, Long userId, String accountId) {
        if (tenantId == null || StrUtil.isBlank(accountId)) {
            throw new ServiceException(1_030_015_000, "回调缺少租户或账号编号");
        }
        return TenantUtils.execute(tenantId, () -> {
            ChannelUnipileCredentialDO c = credential();
            JsonNode acc = UnipileClient.getAccount(props, c.getApiKey(), accountId);
            String phone = StrUtil.blankToDefault(acc.path("name").asText(null), acc.path("username").asText(null));
            ChannelWaAccountDO a = accountMapper.selectByPhoneNumberId(accountId);
            if (a == null) {
                a = ChannelWaAccountDO.builder()
                        .platform(ChannelWaAccountDO.PLATFORM_WHATSAPP)
                        .authType(ChannelWaAccountDO.AUTH_UNIPILE)
                        .phoneNumberId(accountId)
                        .displayPhone(phone)
                        .userId(userId)
                        .enabled(true)
                        .connState(ChannelWaAccountDO.CONN_OPEN)
                        .build();
                accountMapper.insert(a);
            } else {
                accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId())
                        .setDisplayPhone(phone).setConnState(ChannelWaAccountDO.CONN_OPEN).setEnabled(true));
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
        return UnipileClient.sendText(props, key, a.getPhoneNumberId(), chatId, text);
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
        switch (type) {
            case "message.new" -> ingest(a, payload);
            case "account.status" -> {
                String status = payload.path("status").asText("");
                String conn = "OK".equalsIgnoreCase(status) ? ChannelWaAccountDO.CONN_OPEN : ChannelWaAccountDO.CONN_CLOSE;
                if (!conn.equals(a.getConnState())) {
                    accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setConnState(conn));
                }
            }
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
        return TenantUtils.execute(tenantId, () -> credential().getApiKey());
    }

    private static String mask(String key) {
        if (StrUtil.isBlank(key)) {
            return "";
        }
        return "pk_****" + StrUtil.subSuf(key, Math.min(4, key.length()));
    }
}
