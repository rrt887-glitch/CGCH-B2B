package cn.iocoder.yudao.module.channel.service.ycloud;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.ChannelWhatsAppService;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.WA_API_FAIL;

/**
 * WhatsApp 经 YCloud 接入：客户在系统里点「开始连接」→ 打开 YCloud 托管的 Meta 授权页（Partner Direct Link）
 * → 选择企业、号码，共存模式下用手机 WhatsApp Business App 扫码 → YCloud 推送 whatsapp.business_account.updated
 * → 按绑定时的客户编号找到租户和成员，建立渠道账号。之后客户消息、手机端发出的消息、发送状态都由 YCloud 推送过来，
 * 转换成与 Meta 相同的结构后复用 WhatsApp 的入库、AI 回复与线索建档逻辑。
 */
@Service
@Slf4j
public class ChannelYcloudService {

    /** 绑定时的客户编号：cgch_{租户}_{成员}_{随机}，YCloud 在绑定成功的推送里原样带回 */
    private static final String REF_PREFIX = "cgch_";
    private static final String TOKEN_PREFIX = "ycloud:";

    private final ScheduledExecutorService retry = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "channel-ycloud");
        t.setDaemon(true);
        return t;
    });
    /** 最近处理过的推送编号，YCloud 失败重试时去重 */
    private final Map<String, Boolean> seen = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> e) {
            return size() > 5000;
        }
    });

    @Resource
    private ChannelOAuthProperties channelOAuthProperties;
    @Resource
    private ChannelWhatsAppService whatsAppService;
    @Resource
    private ChannelWaAccountMapper channelWaAccountMapper;

    private ChannelOAuthProperties.Ycloud cfg() {
        return channelOAuthProperties.getYcloud();
    }

    public boolean enabled() {
        return cfg().configured();
    }

    // ==================== 扫码绑定 ====================

    public record Link(String url, String ref, long expiresAt) {
    }

    public Link createLink(Long userId, boolean coexist) {
        if (!enabled()) {
            throw exception(WA_API_FAIL, "WhatsApp 接入还没有开通，请联系管理员配置 YCloud");
        }
        String ref = REF_PREFIX + TenantContextHolder.getRequiredTenantId() + "_" + userId + "_" + IdUtil.fastSimpleUUID().substring(0, 10);
        try {
            YcloudClient.OnboardingLink l = YcloudClient.createOnboardingLink(cfg(), ref, coexist);
            return new Link(l.url(), ref, l.expiresAt());
        } catch (WaGraphClient.WaApiException e) {
            throw exception(WA_API_FAIL, e.getMessage());
        }
    }

    /** 绑定结果：绑定成功后返回新建（或更新）的渠道账号；还没完成返回 null */
    public ChannelWaAccountDO result(Long userId, String ref) {
        if (StrUtil.isBlank(ref) || !ref.startsWith(REF_PREFIX + TenantContextHolder.getRequiredTenantId() + "_" + userId + "_")) {
            return null;
        }
        return channelWaAccountMapper.selectOne(ChannelWaAccountDO::getVerifyToken, TOKEN_PREFIX + ref);
    }

    // ==================== Webhook ====================

    /** @return false 表示签名无效 */
    public boolean handleWebhook(byte[] raw, String signature) {
        if (!YcloudClient.verifySignature(cfg().getWebhookSecret(), raw, signature)) {
            log.warn("[handleWebhook][YCloud 推送签名无效]");
            return false;
        }
        JsonNode e = YcloudClient.parse(raw);
        String id = e.path("id").asText("");
        if (!id.isEmpty() && seen.put(id, Boolean.TRUE) != null) {
            return true;
        }
        try {
            switch (e.path("type").asText("")) {
                case "whatsapp.business_account.updated" -> onAccountUpdated(e.path("whatsappBusinessAccount"));
                case "whatsapp.inbound_message.received" -> onInbound(e.path("whatsappInboundMessage"));
                case "whatsapp.smb.message.echoes" -> onEcho(e.path("whatsappMessage"));
                case "whatsapp.message.updated" -> onStatus(e.path("whatsappMessage"));
                default -> {
                }
            }
        } catch (Exception ex) {
            seen.remove(id); // 让 YCloud 重试
            throw ex;
        }
        return true;
    }

    private void onAccountUpdated(JsonNode w) {
        String ref = w.path("partnerCustomerId").asText("");
        String wabaId = w.path("id").asText("");
        if (!"PARTNER_ADDED".equals(w.path("updateEvent").asText()) || !ref.startsWith(REF_PREFIX) || wabaId.isEmpty()) {
            return;
        }
        String[] p = ref.substring(REF_PREFIX.length()).split("_");
        if (p.length < 3 || !StrUtil.isNumeric(p[0]) || !StrUtil.isNumeric(p[1])) {
            return;
        }
        long tenantId = Long.parseLong(p[0]);
        long userId = Long.parseLong(p[1]);
        if (!bind(tenantId, userId, ref, wabaId)) {
            // 号码可能还在注册中，稍后再查两次
            retry.schedule(() -> bind(tenantId, userId, ref, wabaId), 10, TimeUnit.SECONDS);
            retry.schedule(() -> bind(tenantId, userId, ref, wabaId), 60, TimeUnit.SECONDS);
        }
    }

    /** 查 WABA 下的号码，逐个建立 / 更新渠道账号；返回是否查到号码 */
    private boolean bind(long tenantId, long userId, String ref, String wabaId) {
        List<YcloudClient.PhoneNumber> numbers;
        try {
            numbers = YcloudClient.listPhoneNumbers(cfg(), wabaId);
        } catch (Exception ex) {
            log.warn("[bind][WABA {} 查号码失败] {}", wabaId, ex.getMessage());
            return false;
        }
        if (numbers.isEmpty()) {
            return false;
        }
        for (YcloudClient.PhoneNumber n : numbers) {
            String phone = YcloudClient.e164(n.phoneNumber());
            if (phone.isEmpty()) {
                continue;
            }
            ChannelWaAccountDO exist = TenantUtils.executeIgnore(() -> channelWaAccountMapper.selectYcloudByPhone(phone));
            if (exist != null && !Objects.equals(exist.getTenantId(), tenantId)) {
                log.warn("[bind][号码 {} 已被其他企业绑定，跳过]", phone);
                continue;
            }
            TenantUtils.execute(tenantId, () -> {
                ChannelWaAccountDO a = ChannelWaAccountDO.builder().userId(userId)
                        .name(StrUtil.blankToDefault(n.verifiedName(), "WhatsApp " + phone))
                        .platform(ChannelWaAccountDO.PLATFORM_WHATSAPP).authType(ChannelWaAccountDO.AUTH_YCLOUD)
                        .phoneNumberId(StrUtil.blankToDefault(n.id(), "yc_" + phone.substring(1))).wabaId(wabaId)
                        .displayPhone(phone).verifyToken(TOKEN_PREFIX + ref).enabled(true).build();
                if (exist == null) {
                    channelWaAccountMapper.insert(a);
                    log.info("[bind][租户 {} 成员 {} 经 YCloud 绑定 WhatsApp {}]", tenantId, userId, phone);
                } else {
                    a.setId(exist.getId());
                    channelWaAccountMapper.updateById(a);
                }
            });
        }
        return true;
    }

    private ChannelWaAccountDO account(String businessPhone) {
        String phone = YcloudClient.e164(businessPhone);
        if (phone.isEmpty()) {
            return null;
        }
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> channelWaAccountMapper.selectYcloudByPhone(phone));
        if (a == null || !Boolean.TRUE.equals(a.getEnabled())) {
            log.info("[account][YCloud 推送的号码 {} 未绑定或已停用]", phone);
            return null;
        }
        return a;
    }

    private void onInbound(JsonNode m) {
        ChannelWaAccountDO a = account(m.path("to").asText());
        if (a == null) {
            return;
        }
        String name = m.path("customerProfile").path("name").asText(null);
        TenantUtils.execute(a.getTenantId(), () -> whatsAppService.ycloudInbound(a, toMeta(m), name));
    }

    private void onEcho(JsonNode m) {
        ChannelWaAccountDO a = account(m.path("from").asText());
        if (a == null) {
            return;
        }
        TenantUtils.execute(a.getTenantId(), () -> whatsAppService.ycloudEcho(a, toMeta(m), m.path("to").asText()));
    }

    private void onStatus(JsonNode m) {
        ChannelWaAccountDO a = account(m.path("from").asText());
        if (a == null) {
            return;
        }
        String error = StrUtil.blankToDefault(m.path("errorMessage").asText(null),
                m.path("whatsappApiError").path("message").asText(null));
        TenantUtils.execute(a.getTenantId(), () -> whatsAppService.ycloudStatus(a, m.path("wamid").asText(null),
                m.path("id").asText(null), m.path("status").asText(""), error));
    }

    /** YCloud 的消息对象与 Meta 基本一致：补上 Meta 的 id（wamid）、from（纯数字）、timestamp（秒）；媒体编号换成可下载的地址 */
    static JsonNode toMeta(JsonNode m) {
        ObjectNode o = m.deepCopy();
        o.put("id", StrUtil.blankToDefault(m.path("wamid").asText(null), m.path("id").asText("")));
        o.put("from", YcloudClient.e164(m.path("from").asText()).replace("+", ""));
        long ts;
        try {
            ts = Instant.parse(StrUtil.blankToDefault(m.path("sendTime").asText(null), m.path("createTime").asText(""))).getEpochSecond();
        } catch (Exception e) {
            ts = Instant.now().getEpochSecond();
        }
        o.put("timestamp", String.valueOf(ts));
        String type = m.path("type").asText("");
        if (o.path(type).isObject() && o.path(type).hasNonNull("link")) {
            ((ObjectNode) o.path(type)).put("id", o.path(type).path("link").asText());
        }
        return o;
    }

}
