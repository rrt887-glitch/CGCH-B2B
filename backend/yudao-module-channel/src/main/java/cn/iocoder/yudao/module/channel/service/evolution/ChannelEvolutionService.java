package cn.iocoder.yudao.module.channel.service.evolution;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.WA_API_FAIL;

/**
 * WhatsApp 扫码接入（Evolution API，WhatsApp 网页版协议），与原网站「WhatsApp」渠道的接入方式一致：
 * 客户输入手机号拿到 8 位配对码，在手机 WhatsApp「关联设备 → 改用电话号码关联」输入；或切换为二维码，用手机扫码。
 * <p>
 * 流程：建 Evolution 实例（名称 cgch-{租户}-{成员}-{随机}，待绑定信息存 Redis）→ 前端轮询二维码 / 配对码 →
 * 手机确认后 Evolution 推送 connection.update（state=open）或轮询时查到已连接 → 建立渠道账号。
 * 之后客户消息、手机端发出的消息、发送状态由 Evolution 推送过来，转换成 Meta 消息结构后复用 WhatsApp 的入库、AI 回复与线索建档。
 */
@Service
@Slf4j
public class ChannelEvolutionService {

    public static final String WEBHOOK_PATH = "/admin-api/channel/evolution/webhook";
    private static final String NAME_PREFIX = "cgch-";
    private static final String PENDING_KEY = "cgch:evo:pending:";
    private static final Duration PENDING_TTL = Duration.ofMinutes(20);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private ChannelOAuthProperties channelOAuthProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChannelWaAccountMapper accountMapper;
    @Resource
    @Lazy
    private ChannelWhatsAppService whatsAppService;

    private ChannelOAuthProperties.Evolution cfg() {
        return channelOAuthProperties.getEvolution();
    }

    public boolean enabled() {
        return cfg().configured();
    }

    private void requireEnabled() {
        if (!enabled()) {
            throw exception(AI_LLM_FAIL, "WhatsApp 扫码接入还没有开通，请联系管理员");
        }
    }

    private String webhookUrl() {
        String base = StrUtil.blankToDefault(StrUtil.trim(cfg().getWebhookBase()), channelOAuthProperties.getRedirectBase());
        return StrUtil.removeSuffix(base, "/") + WEBHOOK_PATH;
    }

    // ==================== 待绑定 ====================

    /** 待绑定：建实例时记下租户、成员、推送校验令牌、实例令牌；重新扫码时带上原账号编号 */
    record Pending(Long tenantId, Long userId, String token, String hash, Long accountId) {
    }

    private void savePending(String name, Pending p) {
        ObjectNode n = JSON.createObjectNode().put("tenantId", p.tenantId()).put("userId", p.userId())
                .put("token", p.token()).put("hash", p.hash());
        if (p.accountId() != null) {
            n.put("accountId", p.accountId());
        }
        stringRedisTemplate.opsForValue().set(PENDING_KEY + name, n.toString(), PENDING_TTL);
    }

    private Pending pending(String name) {
        String s = stringRedisTemplate.opsForValue().get(PENDING_KEY + name);
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            JsonNode n = JSON.readTree(s);
            return new Pending(n.path("tenantId").asLong(), n.path("userId").asLong(), n.path("token").asText(null),
                    n.path("hash").asText(null), n.hasNonNull("accountId") ? n.path("accountId").asLong() : null);
        } catch (Exception e) {
            return null;
        }
    }

    private String sessionPrefix(Long userId) {
        return NAME_PREFIX + TenantContextHolder.getRequiredTenantId() + "-" + userId + "-";
    }

    // ==================== 扫码 / 配对码 ====================

    /**
     * 连接进度。status：connecting 等待手机确认（附二维码或配对码）；open 已连接；expired 已失效（需重新开始）
     */
    public record Session(String session, String status, String qr, String pairingCode, Long accountId, String displayPhone) {
        static Session expired(String session) {
            return new Session(session, "expired", null, null, null, null);
        }
    }

    /**
     * 开始连接
     *
     * @param number    手机号（含国家码，纯数字）：填了返回配对码，不填返回二维码
     * @param accountId 重新扫码时原账号编号（连接后沿用原账号与会话）
     */
    public Session start(Long userId, String number, Long accountId) {
        requireEnabled();
        String phone = StrUtil.isBlank(number) ? null : EvolutionClient.digits(number);
        if (phone != null && (phone.length() < 8 || phone.length() > 15)) {
            throw exception(AI_LLM_FAIL, "请输入正确的手机号（含国家码）");
        }
        if (accountId != null) {
            ChannelWaAccountDO a = whatsAppService.validateAccount(userId, accountId);
            if (!a.isEvolution()) {
                throw exception(AI_LLM_FAIL, "这个账号不是扫码接入的");
            }
        }
        String name = sessionPrefix(userId) + IdUtil.fastSimpleUUID().substring(0, 10);
        String token = IdUtil.fastSimpleUUID();
        try {
            EvolutionClient.Created c = EvolutionClient.createInstance(cfg(), name, phone, webhookUrl(), token);
            savePending(name, new Pending(TenantContextHolder.getRequiredTenantId(), userId, token, c.token(), accountId));
            return new Session(name, "connecting", c.qr().base64(), c.qr().pairingCode(), null, null);
        } catch (WaGraphClient.WaApiException e) {
            CompletableFuture.runAsync(() -> quietDelete(name));
            throw exception(WA_API_FAIL, e.getMessage());
        }
    }

    /** 轮询连接进度 */
    public Session status(Long userId, String session) {
        if (StrUtil.isBlank(session) || !session.startsWith(sessionPrefix(userId))) {
            return Session.expired(session);
        }
        ChannelWaAccountDO a = findAccount(session);
        if (a != null && ChannelWaAccountDO.CONN_OPEN.equals(a.getConnState())) {
            return new Session(session, "open", null, null, a.getId(), a.getDisplayPhone());
        }
        Pending p = pending(session);
        if (p == null) {
            return Session.expired(session);
        }
        try {
            String state = EvolutionClient.connectionState(cfg(), session);
            if ("open".equals(state)) { // 推送还没到（或推送不通）：直接查实例信息完成绑定
                EvolutionClient.Info info = EvolutionClient.fetchInstance(cfg(), session);
                ChannelWaAccountDO bound = bind(session, p, info.ownerJid(), info.profileName());
                return bound == null ? Session.expired(session)
                        : new Session(session, "open", null, null, bound.getId(), bound.getDisplayPhone());
            }
            if (state == null || "close".equals(state) || "refused".equals(state)) {
                return Session.expired(session);
            }
            EvolutionClient.QrCode qr = EvolutionClient.currentQr(cfg(), session);
            return new Session(session, "connecting", qr.base64(), qr.pairingCode(), null, null);
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
    }

    /** 关闭弹窗、放弃连接：删除还没绑定的实例 */
    public void cancel(Long userId, String session) {
        if (StrUtil.isBlank(session) || !session.startsWith(sessionPrefix(userId)) || findAccount(session) != null) {
            return;
        }
        stringRedisTemplate.delete(PENDING_KEY + session);
        CompletableFuture.runAsync(() -> quietDelete(session));
    }

    private ChannelWaAccountDO findAccount(String instance) {
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(instance));
        return a != null && a.isEvolution() ? a : null;
    }

    /**
     * 连接成功：建立 / 更新渠道账号。同一企业里同一个号码重新扫码时沿用原账号（保留会话），并删除旧实例。
     * 推送和轮询可能同时到达，加锁避免重复建账号。
     */
    synchronized ChannelWaAccountDO bind(String instance, Pending p, String ownerJid, String profileName) {
        ChannelWaAccountDO done = findAccount(instance);
        if (done != null) {
            if (!ChannelWaAccountDO.CONN_OPEN.equals(done.getConnState())) {
                TenantUtils.execute(done.getTenantId(), () -> accountMapper.updateById(
                        new ChannelWaAccountDO().setId(done.getId()).setConnState(ChannelWaAccountDO.CONN_OPEN)));
                done.setConnState(ChannelWaAccountDO.CONN_OPEN);
            }
            return done;
        }
        String digits = EvolutionClient.digits(ownerJid);
        if (digits.isEmpty()) {
            log.warn("[bind][实例 {} 已连接但没有号码]", instance);
            return null;
        }
        String phone = "+" + digits;
        ChannelWaAccountDO[] result = new ChannelWaAccountDO[1];
        TenantUtils.execute(p.tenantId(), () -> {
            ChannelWaAccountDO old = p.accountId() == null ? null : accountMapper.selectById(p.accountId());
            if (old == null || !old.isEvolution()) {
                old = accountMapper.selectOne(new LambdaQueryWrapperX<ChannelWaAccountDO>()
                        .eq(ChannelWaAccountDO::getAuthType, ChannelWaAccountDO.AUTH_EVOLUTION)
                        .eq(ChannelWaAccountDO::getDisplayPhone, phone).last("LIMIT 1"));
            }
            ChannelWaAccountDO a = ChannelWaAccountDO.builder().userId(old == null ? p.userId() : old.getUserId())
                    .name(StrUtil.blankToDefault(profileName, "WhatsApp " + phone))
                    .platform(ChannelWaAccountDO.PLATFORM_WHATSAPP).authType(ChannelWaAccountDO.AUTH_EVOLUTION)
                    .phoneNumberId(instance).displayPhone(phone).accessToken(p.hash()).appSecret(p.token())
                    .connState(ChannelWaAccountDO.CONN_OPEN).enabled(true).build();
            if (old == null) {
                accountMapper.insert(a);
                log.info("[bind][租户 {} 成员 {} 扫码接入 WhatsApp {}]", p.tenantId(), p.userId(), phone);
            } else {
                a.setId(old.getId());
                accountMapper.updateById(a);
                String oldInstance = old.getPhoneNumberId();
                if (!instance.equals(oldInstance)) {
                    CompletableFuture.runAsync(() -> quietDelete(oldInstance));
                }
                log.info("[bind][租户 {} 账号 {} 重新扫码连接 WhatsApp {}]", p.tenantId(), old.getId(), phone);
            }
            result[0] = a;
        });
        stringRedisTemplate.delete(PENDING_KEY + instance);
        if (result[0] != null) {
            scheduleHistory(result[0]);
        }
        return result[0];
    }

    private void quietDelete(String instance) {
        try {
            EvolutionClient.deleteInstance(cfg(), instance);
        } catch (Exception e) {
            log.info("[quietDelete][删除实例 {}] {}", instance, e.getMessage());
        }
    }

    // ==================== 聊天记录 ====================

    /** 最多导入的历史消息条数 */
    private static final int HISTORY_MAX = 200000;
    private static final int HISTORY_PAGE = 500;
    private final java.util.concurrent.ScheduledExecutorService historyExecutor =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "channel-evolution-history");
                t.setDaemon(true);
                return t;
            });

    /**
     * 关联后手机会分几批把聊天记录发给 Evolution（存进它的数据库），推送可能因为太大而发不过来，
     * 所以连接成功后分几次从 Evolution 数据库拉取，按消息编号去重。
     */
    private void scheduleHistory(ChannelWaAccountDO a) {
        for (int delay : new int[]{15, 60, 180, 600}) {
            historyExecutor.schedule(() -> {
                try {
                    ChannelWaAccountDO fresh = findAccount(a.getPhoneNumberId());
                    if (fresh != null) {
                        TenantUtils.execute(fresh.getTenantId(), () -> syncHistory(fresh));
                    }
                } catch (Exception e) {
                    log.warn("[scheduleHistory][账号 {} 拉取聊天记录失败] {}", a.getId(), e.getMessage());
                }
            }, delay, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    /** 手动同步：聊天记录可能很多，放到后台执行，立即返回 */
    public void syncHistoryAsync(ChannelWaAccountDO a) {
        requireEnabled();
        historyExecutor.execute(() -> {
            try {
                TenantUtils.execute(a.getTenantId(), () -> syncHistory(a));
            } catch (Exception e) {
                log.warn("[syncHistoryAsync][账号 {} 拉取聊天记录失败] {}", a.getId(), e.getMessage());
            }
        });
    }

    /** 从 Evolution 数据库拉取聊天记录入库（不计未读、不触发 AI），返回新导入的条数 */
    public int syncHistory(ChannelWaAccountDO a) {
        requireEnabled();
        java.util.List<JsonNode> all = new java.util.ArrayList<>();
        try {
            for (int page = 1; all.size() < HISTORY_MAX; page++) {
                EvolutionClient.MessagePage mp = EvolutionClient.findMessages(cfg(), a.getPhoneNumberId(), page, HISTORY_PAGE);
                all.addAll(mp.records());
                if (mp.records().size() < HISTORY_PAGE || page >= mp.pages()) {
                    break;
                }
            }
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
        all.sort(java.util.Comparator.comparingLong(d -> d.path("messageTimestamp").asLong(0))); // 按时间先后入库，会话里按顺序显示
        int n = 0;
        for (JsonNode d : all) {
            if (history(a, d)) {
                n++;
            }
        }
        log.info("[syncHistory][账号 {} 从 Evolution 拉取 {} 条，可入库 {} 条]", a.getId(), all.size(), n);
        return n;
    }

    /** 删除账号时退出手机上的关联设备并删除实例 */
    public void remove(ChannelWaAccountDO a) {
        if (enabled() && StrUtil.isNotBlank(a.getPhoneNumberId())) {
            quietDelete(a.getPhoneNumberId());
        }
    }

    // ==================== 推送 ====================

    /** @return false 表示校验令牌不对 */
    public boolean handleWebhook(byte[] raw, String token) {
        JsonNode e = EvolutionClient.parse(raw);
        String instance = e.path("instance").asText("");
        String event = e.path("event").asText("");
        JsonNode data = e.path("data");
        if (instance.isEmpty()) {
            return true;
        }
        ChannelWaAccountDO a = findAccount(instance);
        if (a != null) {
            if (!same(a.getAppSecret(), token)) {
                log.warn("[handleWebhook][实例 {} 推送令牌不对]", instance);
                return false;
            }
            TenantUtils.execute(a.getTenantId(), () -> onAccountEvent(a, event, data));
            return true;
        }
        Pending p = pending(instance);
        if (p == null) {
            return true; // 已放弃或已过期的连接
        }
        if (!same(p.token(), token)) {
            log.warn("[handleWebhook][实例 {} 推送令牌不对]", instance);
            return false;
        }
        if ("connection.update".equals(event) && "open".equals(data.path("state").asText())) {
            bind(instance, p, data.path("wuid").asText(null), data.path("profileName").asText(null));
        } else if ("messages.set".equals(event) || "messages.upsert".equals(event)) {
            // 关联后聊天记录可能比连接成功的推送先到：先查实例信息完成绑定，再入库
            ChannelWaAccountDO bound = null;
            try {
                if ("open".equals(EvolutionClient.connectionState(cfg(), instance))) {
                    EvolutionClient.Info info = EvolutionClient.fetchInstance(cfg(), instance);
                    bound = bind(instance, p, info.ownerJid(), info.profileName());
                }
            } catch (Exception ex) {
                log.warn("[handleWebhook][实例 {} 绑定失败] {}", instance, ex.getMessage());
            }
            if (bound != null) {
                ChannelWaAccountDO b = bound;
                TenantUtils.execute(b.getTenantId(), () -> onAccountEvent(b, event, data));
            }
        }
        return true;
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private void onAccountEvent(ChannelWaAccountDO a, String event, JsonNode data) {
        switch (event) {
            case "connection.update" -> {
                String state = data.path("state").asText("");
                String conn = "open".equals(state) ? ChannelWaAccountDO.CONN_OPEN
                        : "close".equals(state) || "refused".equals(state) ? ChannelWaAccountDO.CONN_CLOSE : null;
                if (conn != null && !conn.equals(a.getConnState())) {
                    accountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setConnState(conn));
                    log.info("[onAccountEvent][账号 {} 连接状态 {}]", a.getId(), conn);
                }
            }
            case "messages.upsert" -> {
                if (!Boolean.TRUE.equals(a.getEnabled())) {
                    return;
                }
                if (data.isArray()) {
                    data.forEach(d -> upsert(a, d));
                } else {
                    upsert(a, data);
                }
            }
            case "messages.set" -> { // 关联时同步过来的最近聊天记录：只入库，不计未读、不触发 AI 回复
                JsonNode raw = data.isArray() ? data : data.path("messages");
                java.util.List<JsonNode> list = new java.util.ArrayList<>();
                raw.forEach(list::add);
                list.sort(java.util.Comparator.comparingLong(d -> d.path("messageTimestamp").asLong(0))); // 按时间先后入库
                int n = 0;
                for (JsonNode d : list) {
                    if (history(a, d)) {
                        n++;
                    }
                }
                log.info("[onAccountEvent][账号 {} 同步聊天记录 {} 条]", a.getId(), n);
            }
            case "messages.update" -> {
                if (data.isArray()) {
                    data.forEach(d -> statusUpdate(a, d));
                } else {
                    statusUpdate(a, data);
                }
            }
            default -> {
            }
        }
    }

    private void upsert(ChannelWaAccountDO a, JsonNode d) {
        try {
            JsonNode key = d.path("key");
            String jid = key.path("remoteJid").asText("");
            if (jid.endsWith("@lid")) {
                jid = StrUtil.blankToDefault(key.path("remoteJidAlt").asText(null), key.path("senderPn").asText(""));
            }
            if (!jid.endsWith("@s.whatsapp.net")) {
                return; // 群聊、状态、频道，或无法识别号码的联系人
            }
            String customer = EvolutionClient.digits(jid);
            ObjectNode m = toMeta(d, customer);
            if (customer.isEmpty() || m == null) {
                return;
            }
            if (key.path("fromMe").asBoolean(false)) {
                whatsAppService.evolutionSynced(a, m, customer); // 手机上发出的消息（系统发出的按消息编号去重）
            } else {
                whatsAppService.evolutionInbound(a, m, StrUtil.emptyToNull(d.path("pushName").asText(null)));
            }
        } catch (Exception e) {
            log.warn("[upsert][账号 {}] 消息处理失败 {}", a.getId(), d, e);
        }
    }

    /** 一条历史消息入库；返回是否是可入库的私聊消息 */
    private boolean history(ChannelWaAccountDO a, JsonNode d) {
        try {
            JsonNode key = d.path("key");
            String jid = key.path("remoteJid").asText("");
            if (jid.endsWith("@lid")) {
                jid = StrUtil.blankToDefault(key.path("remoteJidAlt").asText(null), key.path("senderPn").asText(""));
            }
            if (!jid.endsWith("@s.whatsapp.net")) {
                return false;
            }
            String customer = EvolutionClient.digits(jid);
            ObjectNode m = toMeta(d, customer);
            if (customer.isEmpty() || m == null || "reaction".equals(m.path("type").asText())) {
                return false;
            }
            boolean fromMe = key.path("fromMe").asBoolean(false);
            return whatsAppService.evolutionHistory(a, m, customer, fromMe,
                    fromMe ? null : StrUtil.emptyToNull(d.path("pushName").asText(null)));
        } catch (Exception e) {
            log.warn("[history][账号 {}] 聊天记录处理失败 {}", a.getId(), d, e);
            return false;
        }
    }

    private void statusUpdate(ChannelWaAccountDO a, JsonNode d) {
        if (!d.path("fromMe").asBoolean(false)) {
            return;
        }
        String status = switch (d.path("status").asText("")) {
            case "SERVER_ACK" -> "sent";
            case "DELIVERY_ACK" -> "delivered";
            case "READ", "PLAYED" -> "read";
            case "ERROR" -> "failed";
            default -> null;
        };
        String id = d.path("keyId").asText(d.path("key").path("id").asText(""));
        if (status != null && !id.isEmpty()) {
            whatsAppService.evolutionStatus(a, id, status);
        }
    }

    /** Evolution（Baileys）消息转成 Meta 消息结构；不需要入库的消息（协议消息、投票等）返回 null */
    static ObjectNode toMeta(JsonNode d, String customer) {
        JsonNode msg = d.path("message");
        String type = d.path("messageType").asText("");
        String id = d.path("key").path("id").asText("");
        if (id.isEmpty()) {
            return null;
        }
        ObjectNode o = JSON.createObjectNode();
        o.put("id", id);
        o.put("from", customer);
        o.put("timestamp", String.valueOf(d.path("messageTimestamp").asLong(System.currentTimeMillis() / 1000)));
        switch (type) {
            case "conversation", "extendedTextMessage" -> text(o, StrUtil.blankToDefault(msg.path("conversation").asText(null),
                    msg.path("extendedTextMessage").path("text").asText("")));
            case "imageMessage", "videoMessage", "audioMessage", "documentMessage", "stickerMessage" -> {
                String kind = type.replace("Message", "");
                JsonNode src = msg.path(type);
                ObjectNode media = o.putObject(kind);
                media.put("id", "evo:" + id);
                media.put("mime_type", src.path("mimetype").asText(null));
                if (src.hasNonNull("caption")) {
                    media.put("caption", src.path("caption").asText());
                }
                if (src.hasNonNull("fileName")) {
                    media.put("filename", src.path("fileName").asText());
                }
                o.put("type", kind);
            }
            case "locationMessage", "liveLocationMessage" -> {
                JsonNode src = msg.path(type);
                o.put("type", "location");
                o.putObject("location").put("latitude", src.path("degreesLatitude").asText())
                        .put("longitude", src.path("degreesLongitude").asText())
                        .put("name", src.path("name").asText("")).put("address", src.path("address").asText(""));
            }
            case "reactionMessage" -> {
                o.put("type", "reaction");
                o.putObject("reaction").put("emoji", msg.path("reactionMessage").path("text").asText(""));
            }
            case "contactMessage" -> text(o, "[名片] " + msg.path("contactMessage").path("displayName").asText(""));
            case "contactsArrayMessage" -> text(o, "[名片] " + msg.path("contactsArrayMessage").path("displayName").asText(""));
            case "buttonsResponseMessage" -> text(o, msg.path(type).path("selectedDisplayText").asText(""));
            case "templateButtonReplyMessage" -> text(o, msg.path(type).path("selectedDisplayText").asText(""));
            case "listResponseMessage" -> text(o, msg.path(type).path("title").asText(""));
            case "protocolMessage", "senderKeyDistributionMessage", "messageContextInfo", "pollUpdateMessage",
                 "editedMessage", "keepInChatMessage", "pinInChatMessage", "unknown", "" -> {
                return null;
            }
            default -> o.put("type", type);
        }
        return o;
    }

    private static void text(ObjectNode o, String body) {
        o.put("type", "text");
        o.putObject("text").put("body", body);
    }

    // ==================== 发送与媒体 ====================

    public String sendText(ChannelWaAccountDO a, String to, String text) {
        requireEnabled();
        return EvolutionClient.sendText(cfg(), a.getPhoneNumberId(), to, text);
    }

    public String sendMedia(ChannelWaAccountDO a, String to, String kind, byte[] content, String mimeType,
                            String fileName, String caption) {
        requireEnabled();
        return EvolutionClient.sendMedia(cfg(), a.getPhoneNumberId(), to, kind, content, mimeType, fileName, caption);
    }

    public EvolutionClient.Media download(ChannelWaAccountDO a, String messageId) {
        requireEnabled();
        return EvolutionClient.downloadMedia(cfg(), a.getPhoneNumberId(), messageId);
    }

}
