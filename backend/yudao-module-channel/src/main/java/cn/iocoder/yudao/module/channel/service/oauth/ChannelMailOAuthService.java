package cn.iocoder.yudao.module.channel.service.oauth;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.mail.ChannelMailAccountDO;
import cn.iocoder.yudao.module.channel.dal.mysql.mail.ChannelMailAccountMapper;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties.Provider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.MAIL_OAUTH_FAIL;

/**
 * 邮箱 OAuth 授权：Gmail（Google）与 Outlook / Microsoft 365（Microsoft）。
 * 流程：前端打开授权页 → 用户在 Google / Microsoft 登录并同意 → 回调换取令牌 → 绑定邮箱；
 * 收发信时用 XOAUTH2 登录 IMAP / SMTP，访问令牌过期前自动刷新。
 */
@Service
@Slf4j
public class ChannelMailOAuthService {

    public static final String AUTH_OAUTH2 = "oauth2";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final String STATE_KEY = "cgch:mail-oauth:state:";
    /** 提前多久刷新访问令牌 */
    private static final int REFRESH_AHEAD_SECONDS = 120;

    @Resource
    private ChannelOAuthProperties channelOAuthProperties;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChannelMailAccountMapper channelMailAccountMapper;

    /** 已配置的服务商，前端据此显示授权按钮 */
    public List<String> providers() {
        List<String> l = new ArrayList<>();
        if (channelOAuthProperties.getGoogle().configured()) {
            l.add("google");
        }
        if (channelOAuthProperties.getMicrosoft().configured()) {
            l.add("microsoft");
        }
        if (channelOAuthProperties.getMeta().configured()) {
            l.add("meta");
        }
        return l;
    }

    private Provider provider(String name) {
        Provider p = channelOAuthProperties.get(StrUtil.nullToEmpty(name));
        if (p == null || !p.configured()) {
            throw exception(MAIL_OAUTH_FAIL, "未配置 " + name + " 授权应用，请管理员在服务器环境变量中设置 Client ID 和 Secret");
        }
        return p;
    }

    private String redirectUri(String provider) {
        return StrUtil.removeSuffix(channelOAuthProperties.getRedirectBase(), "/") + "/admin-api/channel/oauth/" + provider + "/callback";
    }

    private static String form(Map<String, String> m) {
        return m.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
    }

    /** 生成授权地址；state 记录租户和用户，10 分钟内有效、只能用一次 */
    public String authorizeUrl(Long userId, String providerName) {
        Provider p = provider(providerName);
        String state = IdUtil.fastSimpleUUID();
        stringRedisTemplate.opsForValue().set(STATE_KEY + state,
                TenantContextHolder.getTenantId() + ":" + userId + ":" + providerName, 10, TimeUnit.MINUTES);
        Map<String, String> q = new LinkedHashMap<>();
        q.put("client_id", p.getClientId());
        q.put("redirect_uri", redirectUri(providerName));
        q.put("response_type", "code");
        q.put("scope", p.getScope());
        q.put("state", state);
        if ("google".equals(providerName)) {
            q.put("access_type", "offline"); // 需要刷新令牌
            q.put("prompt", "consent");
        } else {
            q.put("prompt", "select_account");
        }
        return p.getAuthUrl() + (p.getAuthUrl().contains("?") ? "&" : "?") + form(q);
    }

    /** 授权回调：换令牌、取邮箱、绑定或更新邮箱账号，返回绑定的邮箱 */
    public String callback(String providerName, String code, String state, String error) {
        if (StrUtil.isNotBlank(error)) {
            throw exception(MAIL_OAUTH_FAIL, "授权被取消或拒绝：" + error);
        }
        String saved = StrUtil.isBlank(state) ? null : stringRedisTemplate.opsForValue().getAndDelete(STATE_KEY + state);
        if (saved == null) {
            throw exception(MAIL_OAUTH_FAIL, "授权链接已过期，请重新发起授权");
        }
        String[] parts = saved.split(":");
        Long tenantId = Long.valueOf(parts[0]);
        Long userId = Long.valueOf(parts[1]);
        if (!providerName.equals(parts[2])) {
            throw exception(MAIL_OAUTH_FAIL, "授权服务商不匹配");
        }
        Provider p = provider(providerName);
        JsonNode token = post(p.getTokenUrl(), Map.of("grant_type", "authorization_code", "code", StrUtil.nullToEmpty(code),
                "redirect_uri", redirectUri(providerName), "client_id", p.getClientId(), "client_secret", p.getClientSecret()));
        String access = token.path("access_token").asText("");
        String refresh = token.path("refresh_token").asText("");
        if (access.isBlank()) {
            throw exception(MAIL_OAUTH_FAIL, "没有拿到访问令牌");
        }
        String email = email(providerName, p, token, access);
        if (StrUtil.isBlank(email)) {
            throw exception(MAIL_OAUTH_FAIL, "无法获取授权邮箱地址");
        }
        String mail = email.trim().toLowerCase(Locale.ROOT);
        LocalDateTime expire = LocalDateTime.now().plusSeconds(token.path("expires_in").asLong(3600));
        TenantUtils.execute(tenantId, () -> {
            ChannelMailAccountDO old = channelMailAccountMapper.selectByEmail(mail);
            ChannelMailAccountDO a = old != null ? old : new ChannelMailAccountDO();
            a.setUserId(old != null ? old.getUserId() : userId);
            a.setProvider("google".equals(providerName) ? "gmail" : "outlook");
            a.setEmail(mail);
            if (StrUtil.isBlank(a.getDisplayName())) {
                a.setDisplayName(StrUtil.blankToDefault(token.path("name").asText(null), mail));
            }
            a.setImapHost(p.getImapHost()).setImapPort(p.getImapPort()).setImapSsl(p.isImapSsl())
                    .setSmtpHost(p.getSmtpHost()).setSmtpPort(p.getSmtpPort()).setSmtpSsl(p.isSmtpSsl())
                    .setUsername(mail).setPassword("").setAuthType(AUTH_OAUTH2)
                    .setOauthAccessToken(access).setOauthExpireTime(expire).setEnabled(true).setLastSyncError("");
            if (!refresh.isBlank()) {
                a.setOauthRefreshToken(refresh);
            } else if (old == null || StrUtil.isBlank(old.getOauthRefreshToken())) {
                throw exception(MAIL_OAUTH_FAIL, "没有拿到刷新令牌，请在授权页重新同意离线访问");
            }
            if (old == null) {
                channelMailAccountMapper.insert(a);
            } else {
                channelMailAccountMapper.updateById(a);
            }
        });
        log.info("[callback][{} 授权绑定邮箱 {}，租户 {}]", providerName, mail, tenantId);
        return mail;
    }

    private String email(String providerName, Provider p, JsonNode token, String access) {
        if ("google".equals(providerName) && StrUtil.isNotBlank(p.getUserinfoUrl())) {
            try {
                HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(p.getUserinfoUrl()))
                        .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + access).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                JsonNode u = JSON.readTree(r.body());
                if (u.hasNonNull("email")) {
                    return u.get("email").asText();
                }
            } catch (Exception e) {
                log.warn("[email][Google userinfo 失败] {}", e.getMessage());
            }
        }
        // id_token 直接来自令牌接口（HTTPS），这里只解析载荷取邮箱
        String idToken = token.path("id_token").asText("");
        String[] seg = idToken.split("\\.");
        if (seg.length >= 2) {
            try {
                JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(seg[1]));
                for (String k : new String[]{"email", "preferred_username", "upn"}) {
                    if (claims.hasNonNull(k) && claims.get(k).asText().contains("@")) {
                        return claims.get(k).asText();
                    }
                }
            } catch (Exception e) {
                log.warn("[email][解析 id_token 失败] {}", e.getMessage());
            }
        }
        return null;
    }

    private static JsonNode post(String url, Map<String, String> params) {
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form(params))).build(), HttpResponse.BodyHandlers.ofString());
            JsonNode j = StrUtil.isBlank(r.body()) ? JSON.createObjectNode() : JSON.readTree(r.body());
            if (r.statusCode() >= 300 || j.has("error")) {
                String msg = j.path("error_description").asText(j.path("error").asText("HTTP " + r.statusCode()));
                throw exception(MAIL_OAUTH_FAIL, msg);
            }
            return j;
        } catch (cn.iocoder.yudao.framework.common.exception.ServiceException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw exception(MAIL_OAUTH_FAIL, "请求被中断");
        } catch (Exception e) {
            throw exception(MAIL_OAUTH_FAIL, "无法连接授权服务器：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 收发信前取可用的访问令牌，快过期时用刷新令牌换新的并保存 */
    public synchronized String accessToken(ChannelMailAccountDO a) {
        if (a.getOauthExpireTime() != null && StrUtil.isNotBlank(a.getOauthAccessToken())
                && a.getOauthExpireTime().isAfter(LocalDateTime.now().plusSeconds(REFRESH_AHEAD_SECONDS))) {
            return a.getOauthAccessToken();
        }
        if (StrUtil.isBlank(a.getOauthRefreshToken())) {
            throw exception(MAIL_OAUTH_FAIL, "授权已失效，请重新授权绑定该邮箱");
        }
        Provider p = provider("gmail".equals(a.getProvider()) ? "google" : "microsoft");
        Map<String, String> params = new LinkedHashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", a.getOauthRefreshToken());
        params.put("client_id", p.getClientId());
        params.put("client_secret", p.getClientSecret());
        if ("outlook".equals(a.getProvider())) {
            params.put("scope", p.getScope());
        }
        JsonNode t = post(p.getTokenUrl(), params);
        String access = t.path("access_token").asText("");
        LocalDateTime expire = LocalDateTime.now().plusSeconds(t.path("expires_in").asLong(3600));
        ChannelMailAccountDO u = new ChannelMailAccountDO().setId(a.getId()).setOauthAccessToken(access).setOauthExpireTime(expire);
        if (t.hasNonNull("refresh_token") && !t.get("refresh_token").asText().isBlank()) {
            u.setOauthRefreshToken(t.get("refresh_token").asText()); // Microsoft 会轮换刷新令牌
            a.setOauthRefreshToken(u.getOauthRefreshToken());
        }
        channelMailAccountMapper.updateById(u);
        a.setOauthAccessToken(access).setOauthExpireTime(expire);
        return access;
    }

}
