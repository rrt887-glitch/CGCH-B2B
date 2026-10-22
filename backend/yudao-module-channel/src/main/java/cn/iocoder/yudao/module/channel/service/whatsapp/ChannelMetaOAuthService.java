package cn.iocoder.yudao.module.channel.service.whatsapp;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.whatsapp.ChannelWaAccountMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.META_OAUTH_FAIL;

/**
 * Facebook 登录授权：一次授权接入用户管理的所有 Facebook 主页，以及主页关联的 Instagram 专业账号。
 * <p>
 * 流程：前端打开授权页 → 用户登录 Facebook 并选择主页 → 回调用 code 换用户令牌 → 换长期令牌 →
 * 查询主页列表（每个主页带自己的主页令牌，由长期用户令牌换得的主页令牌不会过期）→
 * 为每个主页、每个关联的 Instagram 账号建立账号，并订阅主页的消息推送。
 * 推送统一发到应用级 Webhook（/admin-api/channel/meta/webhook），按主页 / Instagram 编号分发。
 */
@Service
@Slf4j
public class ChannelMetaOAuthService {

    private static final String STATE_KEY = "cgch:meta-oauth:state:";

    @Resource
    private ChannelOAuthProperties channelOAuthProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChannelWaAccountMapper accountMapper;

    private ChannelOAuthProperties.Meta meta() {
        ChannelOAuthProperties.Meta m = channelOAuthProperties.getMeta();
        if (!m.configured()) {
            throw exception(META_OAUTH_FAIL, "未配置 Meta 应用，请管理员在服务器环境变量中设置 App ID 和 App Secret");
        }
        return m;
    }

    private String redirectUri() {
        return StrUtil.removeSuffix(channelOAuthProperties.getRedirectBase(), "/") + "/admin-api/channel/meta/oauth/callback";
    }

    private static String enc(String v) {
        return URLEncoder.encode(StrUtil.nullToEmpty(v), StandardCharsets.UTF_8);
    }

    /** 调用 Graph API 用的「账号」：只带地址、版本和令牌 */
    private static ChannelWaAccountDO graph(ChannelOAuthProperties.Meta m, String token) {
        return new ChannelWaAccountDO().setApiBase(m.getApiBase()).setApiVersion(m.getApiVersion()).setAccessToken(token);
    }

    /** 生成授权地址；state 记录租户和用户，10 分钟内有效、只能用一次 */
    public String authorizeUrl(Long userId) {
        ChannelOAuthProperties.Meta m = meta();
        String state = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(STATE_KEY + state,
                TenantContextHolder.getTenantId() + ":" + userId, 10, TimeUnit.MINUTES);
        return m.getDialogUrl() + (m.getDialogUrl().contains("?") ? "&" : "?")
                + "client_id=" + enc(m.getClientId()) + "&redirect_uri=" + enc(redirectUri())
                + "&response_type=code&scope=" + enc(m.getScope()) + "&state=" + state;
    }

    /** 授权回调，返回接入结果说明，例如「Facebook 主页 A、Instagram @b」 */
    public String callback(String code, String state, String error) {
        if (StrUtil.isNotBlank(error)) {
            throw exception(META_OAUTH_FAIL, "授权被取消或拒绝：" + error);
        }
        String saved = StrUtil.isBlank(state) ? null : stringRedisTemplate.opsForValue().getAndDelete(STATE_KEY + state);
        if (saved == null) {
            throw exception(META_OAUTH_FAIL, "授权链接已过期，请重新发起授权");
        }
        String[] parts = saved.split(":");
        Long tenantId = Long.valueOf(parts[0]);
        Long userId = Long.valueOf(parts[1]);
        ChannelOAuthProperties.Meta m = meta();
        try {
            ChannelWaAccountDO anon = graph(m, null);
            String shortToken = WaGraphClient.call(anon, "GET", "oauth/access_token?client_id=" + enc(m.getClientId())
                    + "&redirect_uri=" + enc(redirectUri()) + "&client_secret=" + enc(m.getClientSecret())
                    + "&code=" + enc(code), null).path("access_token").asText("");
            if (shortToken.isBlank()) {
                throw exception(META_OAUTH_FAIL, "没有拿到访问令牌");
            }
            // 换成长期用户令牌，由它得到的主页令牌长期有效
            String longToken = WaGraphClient.call(anon, "GET", "oauth/access_token?grant_type=fb_exchange_token&client_id="
                    + enc(m.getClientId()) + "&client_secret=" + enc(m.getClientSecret())
                    + "&fb_exchange_token=" + enc(shortToken), null).path("access_token").asText(shortToken);
            JsonNode pages = WaGraphClient.call(graph(m, longToken), "GET", "me/accounts?limit=100&fields="
                    + enc("id,name,access_token,instagram_business_account{id,username,name}"), null).path("data");
            if (!pages.isArray() || pages.isEmpty()) {
                throw exception(META_OAUTH_FAIL, "没有找到可管理的 Facebook 主页，请确认授权时勾选了主页");
            }
            List<String> done = new ArrayList<>();
            List<String> skipped = new ArrayList<>();
            TenantUtils.execute(tenantId, () -> {
                for (JsonNode p : pages) {
                    String pageToken = p.path("access_token").asText("");
                    if (pageToken.isBlank()) {
                        skipped.add(p.path("name").asText(p.path("id").asText()) + "（没有主页令牌，可能缺少管理权限）");
                        continue;
                    }
                    ChannelWaAccountDO page = upsert(m, userId, ChannelWaAccountDO.PLATFORM_MESSENGER, p.path("id").asText(),
                            p.path("name").asText(null), pageToken, skipped);
                    if (page != null) {
                        try {
                            WaGraphClient.subscribePage(page);
                        } catch (WaGraphClient.WaApiException e) {
                            log.warn("[callback][主页 {} 订阅消息推送失败] {}", page.getPhoneNumberId(), e.getMessage());
                            skipped.add(page.getDisplayPhone() + " 订阅消息推送失败：" + e.getMessage());
                        }
                        done.add("Facebook 主页「" + page.getDisplayPhone() + "」");
                    }
                    JsonNode ig = p.path("instagram_business_account");
                    if (ig.hasNonNull("id")) {
                        String u = ig.path("username").asText(null);
                        ChannelWaAccountDO insta = upsert(m, userId, ChannelWaAccountDO.PLATFORM_INSTAGRAM, ig.path("id").asText(),
                                StrUtil.isNotBlank(u) ? "@" + u : ig.path("name").asText(null), pageToken, skipped);
                        if (insta != null) {
                            done.add("Instagram " + insta.getDisplayPhone());
                        }
                    }
                }
            });
            if (done.isEmpty()) {
                throw exception(META_OAUTH_FAIL, String.join("；", skipped));
            }
            log.info("[callback][租户 {} 用户 {} 通过 Facebook 授权接入 {}]", tenantId, userId, done);
            return "已接入 " + String.join("、", done) + (skipped.isEmpty() ? "" : "。未接入：" + String.join("；", skipped));
        } catch (WaGraphClient.WaApiException e) {
            throw exception(META_OAUTH_FAIL, e.getMessage());
        }
    }

    /** 按平台账号编号新增或更新；已被其他企业绑定的跳过 */
    private ChannelWaAccountDO upsert(ChannelOAuthProperties.Meta m, Long userId, String platform, String id,
                                      String name, String token, List<String> skipped) {
        ChannelWaAccountDO old = TenantUtils.executeIgnore(() -> accountMapper.selectByPhoneNumberId(id));
        String label = StrUtil.blankToDefault(name, id);
        if (old != null && !Objects.equals(old.getTenantId(), TenantContextHolder.getTenantId())) {
            skipped.add(label + "（已被其他企业绑定）");
            return null;
        }
        ChannelWaAccountDO a = old != null ? old : new ChannelWaAccountDO()
                .setUserId(userId).setPlatform(platform).setPhoneNumberId(id).setName(label)
                .setVerifyToken(IdUtil.fastSimpleUUID());
        a.setAuthType(ChannelWaAccountDO.AUTH_OAUTH).setAccessToken(token).setDisplayPhone(label)
                .setApiBase(m.getApiBase()).setApiVersion(m.getApiVersion()).setEnabled(true);
        if (old == null) {
            accountMapper.insert(a);
        } else {
            accountMapper.updateById(a);
        }
        return a;
    }

    /** 已配置时返回 true，前端据此显示「Facebook 登录授权」按钮 */
    public boolean configured() {
        return channelOAuthProperties.getMeta().configured();
    }


}
