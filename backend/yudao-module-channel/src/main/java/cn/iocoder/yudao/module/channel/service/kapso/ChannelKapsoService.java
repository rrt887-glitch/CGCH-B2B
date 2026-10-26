package cn.iocoder.yudao.module.channel.service.kapso;

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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.WA_API_FAIL;

/**
 * WhatsApp 经 Kapso 接入：客户在系统里点「开始连接」→ 打开 Kapso 托管的绑定页（Kapso 的 Meta 应用发起 Meta 官方授权）
 * → 选择企业、号码，共存模式下用手机 WhatsApp Business App 扫码 → 绑定页跳回我们的地址（同时 Kapso 推送 whatsapp.phone_number.created）
 * → 按绑定编号找到租户和成员，建立渠道账号，并给号码配置推送地址（Meta 原始格式）。
 * 之后客户消息、手机端发出的消息、发送状态都按 Meta 推送处理；发消息、媒体、模板走 Kapso 的 Meta 代理接口，复用 Graph API 的代码。
 */
@Service
@Slf4j
public class ChannelKapsoService {

    /** 绑定编号：cgch_{租户}_{成员}_{随机}，作为 Kapso 客户的 external_customer_id */
    private static final String REF_PREFIX = "cgch_";
    private static final String TOKEN_PREFIX = "kapso:";
    public static final String WEBHOOK_PATH = "/admin-api/channel/kapso/webhook";
    public static final String RETURN_PATH = "/admin-api/channel/kapso/return";

    /** 最近处理过的推送编号（X-Idempotency-Key），Kapso 重试时去重 */
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

    private ChannelOAuthProperties.Kapso cfg() {
        return channelOAuthProperties.getKapso();
    }

    @PostConstruct
    public void init() {
        WaGraphClient.setKapsoApiKey(cfg().getApiKey());
    }

    public boolean enabled() {
        return cfg().configured();
    }

    private String base() {
        return StrUtil.removeSuffix(channelOAuthProperties.getRedirectBase(), "/");
    }

    // ==================== 绑定 ====================

    public record Link(String url, String ref, long expiresAt) {
    }

    public Link createLink(Long userId, boolean coexist) {
        if (!enabled()) {
            throw exception(WA_API_FAIL, "WhatsApp 接入还没有开通，请联系管理员配置 Kapso");
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        String ref = REF_PREFIX + tenantId + "_" + userId + "_" + IdUtil.fastSimpleUUID().substring(0, 10);
        try {
            String customerId = KapsoClient.createCustomer(cfg(), "CGCH " + tenantId + "-" + userId, ref);
            String back = base() + RETURN_PATH + "?ref=" + URLEncoder.encode(ref, StandardCharsets.UTF_8);
            KapsoClient.SetupLink l = KapsoClient.createSetupLink(cfg(), customerId, coexist, back, back + "&failed=1");
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

    /** 绑定页跳回：参数可以被伪造，所以向 Kapso 核实号码确实属于这个绑定编号 */
    public boolean handleReturn(String ref, String phoneNumberId, boolean failed) {
        if (failed || StrUtil.hasBlank(ref, phoneNumberId)) {
            return false;
        }
        try {
            return bind(ref, phoneNumberId) != null;
        } catch (Exception e) {
            log.warn("[handleReturn][{} 绑定 {} 失败] {}", ref, phoneNumberId, e.getMessage());
            return false;
        }
    }

    /**
     * 建立 / 更新渠道账号。号码信息与归属都以 Kapso 接口返回为准：号码所属 Kapso 客户的 external_customer_id 必须等于绑定编号。
     * 跳回和推送可能同时到达，加锁避免重复建账号。
     */
    synchronized ChannelWaAccountDO bind(String ref, String phoneNumberId) {
        if (!ref.startsWith(REF_PREFIX)) {
            return null;
        }
        String[] p = ref.substring(REF_PREFIX.length()).split("_");
        if (p.length < 3 || !StrUtil.isNumeric(p[0]) || !StrUtil.isNumeric(p[1])) {
            return null;
        }
        long tenantId = Long.parseLong(p[0]);
        long userId = Long.parseLong(p[1]);
        KapsoClient.PhoneNumber n = KapsoClient.getPhoneNumber(cfg(), phoneNumberId);
        if (StrUtil.isBlank(n.customerId())
                || !ref.equals(KapsoClient.getCustomer(cfg(), n.customerId()).path("external_customer_id").asText(""))) {
            log.warn("[bind][号码 {} 不属于绑定编号 {}]", phoneNumberId, ref);
            return null;
        }
        ChannelWaAccountDO exist = TenantUtils.executeIgnore(() -> channelWaAccountMapper.selectByPhoneNumberId(n.phoneNumberId()));
        if (exist != null && (!exist.isKapso() || !Objects.equals(exist.getTenantId(), tenantId))) {
            log.warn("[bind][号码 {} 已被其他账号绑定，跳过]", n.phoneNumberId());
            return null;
        }
        String phone = StrUtil.blankToDefault(n.displayPhone(), "");
        ChannelWaAccountDO a = ChannelWaAccountDO.builder().userId(userId)
                .name(StrUtil.blankToDefault(n.verifiedName(), "WhatsApp " + phone))
                .platform(ChannelWaAccountDO.PLATFORM_WHATSAPP).authType(ChannelWaAccountDO.AUTH_KAPSO)
                .phoneNumberId(n.phoneNumberId()).wabaId(n.wabaId()).displayPhone(phone)
                .apiBase(cfg().metaBase()).apiVersion(WaGraphClient.DEFAULT_API_VERSION)
                .verifyToken(TOKEN_PREFIX + ref).enabled(true).build();
        TenantUtils.execute(tenantId, () -> {
            if (exist == null) {
                channelWaAccountMapper.insert(a);
                log.info("[bind][租户 {} 成员 {} 经 Kapso 绑定 WhatsApp {}]", tenantId, userId, phone);
            } else {
                a.setId(exist.getId());
                channelWaAccountMapper.updateById(a);
            }
        });
        if (exist == null || !Objects.equals(exist.getVerifyToken(), a.getVerifyToken())) {
            try {
                KapsoClient.createMetaWebhook(cfg(), n.phoneNumberId(), base() + WEBHOOK_PATH);
            } catch (Exception e) {
                // 每个号码只能有一个 Meta 格式的推送地址，重新绑定时已存在会报错，忽略即可
                log.info("[bind][号码 {} 配置推送地址] {}", n.phoneNumberId(), e.getMessage());
            }
        }
        return a;
    }

    // ==================== 推送 ====================

    /** @return false 表示签名无效 */
    public boolean handleWebhook(byte[] raw, String signature, String event, String idempotencyKey) {
        if (!KapsoClient.verifySignature(cfg().getWebhookSecret(), raw, signature)) {
            log.warn("[handleWebhook][Kapso 推送签名无效]");
            return false;
        }
        String key = StrUtil.nullToEmpty(idempotencyKey);
        if (!key.isEmpty() && seen.put(key, Boolean.TRUE) != null) {
            return true;
        }
        JsonNode e = KapsoClient.parse(raw);
        try {
            if ("whatsapp_business_account".equals(e.path("object").asText())) {
                whatsAppService.handleKapsoMeta(e); // 号码的推送：Meta 原始格式
                return true;
            }
            String type = StrUtil.blankToDefault(event, e.path("event").asText(""));
            String pnid = e.path("phone_number_id").asText("");
            switch (type) {
                case "whatsapp.phone_number.created" -> {
                    String ref = e.path("customer").path("external_id").asText(
                            e.path("customer").path("external_customer_id").asText(""));
                    if (!ref.isEmpty() && !pnid.isEmpty()) {
                        bind(ref, pnid);
                    }
                }
                case "whatsapp.phone_number.deleted", "whatsapp.phone_number.offboarded" -> disable(pnid);
                default -> {
                }
            }
        } catch (Exception ex) {
            seen.remove(key); // 让 Kapso 重试
            throw ex;
        }
        return true;
    }

    /** 号码在 Kapso 被删除 / 解绑：停用渠道账号，保留会话记录 */
    private void disable(String phoneNumberId) {
        if (phoneNumberId.isEmpty()) {
            return;
        }
        ChannelWaAccountDO a = TenantUtils.executeIgnore(() -> channelWaAccountMapper.selectByPhoneNumberId(phoneNumberId));
        if (a != null && a.isKapso()) {
            TenantUtils.execute(a.getTenantId(), () ->
                    channelWaAccountMapper.updateById(new ChannelWaAccountDO().setId(a.getId()).setEnabled(false)));
            log.info("[disable][号码 {} 已在 Kapso 解绑，停用账号 {}]", phoneNumberId, a.getId());
        }
    }

}
