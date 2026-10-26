package cn.iocoder.yudao.module.channel.service.kapso;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient.WaApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Kapso 平台接口（https://docs.kapso.ai）：建客户、生成绑定链接（setup link）、查号码、给号码配置推送地址，
 * 以及校验推送签名。请求头 X-API-Key 鉴权。收发消息走 Kapso 的 Meta 代理接口（与 Graph API 一致），见 WaGraphClient。
 */
public final class KapsoClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private KapsoClient() {
    }

    private static JsonNode call(ChannelOAuthProperties.Kapso c, String method, String path, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(c.platformBase() + path)).timeout(Duration.ofSeconds(30))
                    .header("X-API-Key", c.getApiKey()).header("Accept", "application/json");
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300) {
                throw new WaApiException("Kapso：" + errorText(json, resp.statusCode()));
            }
            return json;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("无法连接 Kapso：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 错误信息可能是 {"error":"..."}、{"error":{"message":"..."}} 或 {"errors":[...]} */
    private static String errorText(JsonNode json, int status) {
        JsonNode err = json.path("error");
        if (err.isTextual()) {
            return err.asText();
        }
        if (err.hasNonNull("message")) {
            return err.path("message").asText();
        }
        JsonNode errors = json.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            JsonNode e = errors.path(0);
            return e.isTextual() ? e.asText() : e.path("message").asText(e.toString());
        }
        return "HTTP " + status;
    }

    // ==================== 客户与绑定链接 ====================

    /** 建一个 Kapso 客户，externalId 为我们的绑定编号，返回 Kapso 客户编号 */
    public static String createCustomer(ChannelOAuthProperties.Kapso c, String name, String externalId) {
        ObjectNode body = JSON.createObjectNode();
        ObjectNode cu = body.putObject("customer");
        cu.put("name", name);
        cu.put("external_customer_id", externalId);
        String id = call(c, "POST", "/customers", body).path("data").path("id").asText("");
        if (id.isEmpty()) {
            throw new WaApiException("Kapso 没有返回客户编号");
        }
        return id;
    }

    public static JsonNode getCustomer(ChannelOAuthProperties.Kapso c, String customerId) {
        return call(c, "GET", "/customers/" + enc(customerId), null).path("data");
    }

    public record SetupLink(String url, long expiresAt) {
    }

    /**
     * 生成绑定链接：客户打开后登录 Facebook，选择企业与号码完成绑定。
     *
     * @param coexist true 共存模式（继续用手机 WhatsApp Business App），false 纯 API 号码
     */
    public static SetupLink createSetupLink(ChannelOAuthProperties.Kapso c, String customerId, boolean coexist,
                                            String successUrl, String failureUrl) {
        ObjectNode body = JSON.createObjectNode();
        ObjectNode s = body.putObject("setup_link");
        s.put("success_redirect_url", successUrl);
        s.put("failure_redirect_url", failureUrl);
        s.putArray("allowed_connection_types").add(coexist ? "coexistence" : "dedicated");
        if (StrUtil.isNotBlank(c.getBillingMode())) {
            s.put("meta_billing_mode", c.getBillingMode());
        }
        if (StrUtil.isNotBlank(c.getLanguage())) {
            s.put("language", c.getLanguage());
        }
        JsonNode d = call(c, "POST", "/customers/" + enc(customerId) + "/setup_links", body).path("data");
        String url = d.path("url").asText("");
        if (url.isEmpty()) {
            throw new WaApiException("Kapso 没有返回绑定链接");
        }
        long exp;
        try {
            exp = Instant.parse(d.path("expires_at").asText("")).toEpochMilli();
        } catch (Exception e) {
            exp = System.currentTimeMillis() + Duration.ofDays(30).toMillis();
        }
        return new SetupLink(url, exp);
    }

    // ==================== 号码 ====================

    public record PhoneNumber(String phoneNumberId, String wabaId, String displayPhone, String verifiedName,
                              String customerId, boolean coexistence) {
    }

    public static PhoneNumber getPhoneNumber(ChannelOAuthProperties.Kapso c, String phoneNumberId) {
        JsonNode d = call(c, "GET", "/whatsapp/phone_numbers/" + enc(phoneNumberId), null).path("data");
        String display = StrUtil.blankToDefault(d.path("display_phone_number_normalized").asText(null),
                d.path("display_phone_number").asText(""));
        return new PhoneNumber(StrUtil.blankToDefault(d.path("phone_number_id").asText(null), phoneNumberId),
                d.path("business_account_id").asText(null), e164(display),
                StrUtil.blankToDefault(d.path("verified_name").asText(null), d.path("display_name").asText(null)),
                d.path("customer_id").asText(null), d.path("is_coexistence").asBoolean(false));
    }

    /** 给号码配置推送地址：kind=meta 时 Kapso 原样转发 Meta 的推送（消息、状态、共存模式手机端回显） */
    public static void createMetaWebhook(ChannelOAuthProperties.Kapso c, String phoneNumberId, String url) {
        ObjectNode body = JSON.createObjectNode();
        ObjectNode w = body.putObject("whatsapp_webhook");
        w.put("kind", "meta");
        w.put("url", url);
        w.put("secret_key", c.getWebhookSecret());
        w.put("active", true);
        call(c, "POST", "/whatsapp/phone_numbers/" + enc(phoneNumberId) + "/webhooks", body);
    }

    // ==================== 工具 ====================

    /** 号码统一成 +数字 */
    public static String e164(String phone) {
        String d = phone == null ? "" : phone.replaceAll("\\D", "");
        return d.isEmpty() ? "" : "+" + d;
    }

    /** 推送签名：X-Webhook-Signature = hex(HMAC-SHA256(secret, 原始请求体)) */
    public static boolean verifySignature(String secret, byte[] raw, String header) {
        if (StrUtil.hasBlank(secret, header) || raw == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = HexFormat.of().formatHex(mac.doFinal(raw));
            String got = StrUtil.removePrefix(header.trim(), "sha256=").toLowerCase();
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), got.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    public static JsonNode parse(byte[] raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

}
