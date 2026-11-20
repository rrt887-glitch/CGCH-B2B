package cn.iocoder.yudao.module.channel.service.unipile;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.framework.unipile.ChannelUnipileProperties;
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
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Unipile API v2 客户端。
 *
 * 关键点：
 * 1. 建 Scope / 建 Key / 删账号用平台主 Key；
 * 2. 收发消息、查账号必须用「该租户自己的 Key」，这样 Unipile 侧天然隔离；
 * 3. 发送消息的真实路径是 POST /v2/{account_id}/chats/{chat_id}/messages/send。
 */
public final class UnipileClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private UnipileClient() {
    }

    public static class UnipileException extends RuntimeException {
        public UnipileException(String message) {
            super(message);
        }
    }

    // ==================== Scope / Key ====================

    /** 建隔离边界：reference 存我们自己的 tenantId，便于对账 */
    public static String createScope(ChannelUnipileProperties cfg, String name, String reference) {
        ObjectNode body = JSON.createObjectNode();
        body.put("name", StrUtil.maxLength(name, 255));
        body.put("reference", StrUtil.maxLength(reference, 255));
        JsonNode r = call(cfg, cfg.getServiceKey(), "POST", "/v2/scopes/", body);
        String id = StrUtil.blankToDefault(r.path("id").asText(null), r.path("scope_id").asText(null));
        if (StrUtil.isBlank(id)) {
            throw new UnipileException("创建 Scope 失败：返回里没有 id");
        }
        return id;
    }

    /** 建只属于该 Scope 的 Key，返回明文 Key（只返回一次，调用方要立刻加密存库） */
    public static String createScopeKey(ChannelUnipileProperties cfg, String scopeId, String name, int expireDays) {
        ObjectNode body = JSON.createObjectNode();
        body.put("name", StrUtil.maxLength(name, 255));
        body.put("expires_at", Instant.now().plus(expireDays, ChronoUnit.DAYS).toString());
        ObjectNode access = body.putObject("access");
        access.put("type", "scope");
        access.put("scope_id", scopeId);
        JsonNode r = call(cfg, cfg.getServiceKey(), "POST", "/v2/api-keys/", body);
        String key = StrUtil.blankToDefault(r.path("api_key").asText(null), r.path("key").asText(null));
        if (StrUtil.isBlank(key)) {
            throw new UnipileException("创建租户 Key 失败：返回里没有 api_key");
        }
        return key;
    }

    // ==================== 托管授权（扫码） ====================

    /**
     * 生成托管授权链接。用【租户 Key】调用，账号会直接归属该租户的 Scope。
     * 前端打开这个地址，客户在里面扫码或输配对码。
     */
    public static String createAuthLink(ChannelUnipileProperties cfg, String tenantKey, String redirectUri,
                                        String state, int expireMinutes) {
        ObjectNode body = JSON.createObjectNode();
        body.put("providers", "whatsapp");
        body.put("expires_on", Instant.now().plus(expireMinutes, ChronoUnit.MINUTES).toString());
        body.put("redirect_uri", redirectUri);
        if (StrUtil.isNotBlank(state)) {
            body.put("state", state);
        }
        ObjectNode wa = body.putObject("config").putObject("whatsapp");
        wa.putArray("allow_methods").add("qr").add("pairing");
        JsonNode r = call(cfg, tenantKey, "POST", "/v2/auth/link", body);
        String url = StrUtil.blankToDefault(r.path("url").asText(null), r.path("auth_url").asText(null));
        if (StrUtil.isBlank(url)) {
            throw new UnipileException("生成授权链接失败：返回里没有 url");
        }
        return url;
    }

    // ==================== 账号 ====================

    /** 查账号（用租户 Key 查，越权会 404） */
    public static JsonNode getAccount(ChannelUnipileProperties cfg, String tenantKey, String accountId) {
        return call(cfg, tenantKey, "GET", "/v2/accounts/" + URLEncoder.encode(accountId, StandardCharsets.UTF_8), null);
    }

    public static JsonNode listAccounts(ChannelUnipileProperties cfg, String tenantKey) {
        return call(cfg, tenantKey, "GET", "/v2/accounts?limit=100", null);
    }

    public static void deleteAccount(ChannelUnipileProperties cfg, String key, String accountId) {
        call(cfg, key, "DELETE", "/v2/accounts/" + URLEncoder.encode(accountId, StandardCharsets.UTF_8), null);
    }

    // ==================== 消息 ====================

    /** 发文本，返回 Unipile 的消息编号 */
    public static String sendText(ChannelUnipileProperties cfg, String tenantKey, String accountId,
                                  String chatId, String text) {
        ObjectNode body = JSON.createObjectNode();
        body.put("text", text);
        String path = "/v2/" + enc(accountId) + "/chats/" + enc(chatId) + "/messages/send";
        JsonNode r = call(cfg, tenantKey, "POST", path, body);
        JsonNode id = r.path("message_id");
        if (id.isArray() && id.size() > 0) {
            return id.get(0).asText();
        }
        return id.asText("");
    }

    // ==================== Webhook 验签 ====================

    /**
     * 校验 Unipile 的签名头。
     * 兼容两种格式：`t=时间戳,v0=签名`（HMAC 内容为 "时间戳.原始body"）与直接给十六进制 HMAC。
     */
    public static boolean verifySignature(String secret, String header, byte[] raw) {
        if (StrUtil.isBlank(secret) || StrUtil.isBlank(header) || raw == null) {
            return false;
        }
        try {
            String signedPayload = new String(raw, StandardCharsets.UTF_8);
            String expectHex;
            if (header.contains("v0=") || header.contains("t=")) {
                String ts = "";
                String sig = "";
                for (String part : header.split(",")) {
                    String p = part.trim();
                    if (p.startsWith("t=")) {
                        ts = p.substring(2);
                    } else if (p.startsWith("v0=")) {
                        sig = p.substring(3);
                    }
                }
                if (StrUtil.isBlank(sig)) {
                    return false;
                }
                expectHex = hmacHex(secret, ts + "." + signedPayload);
                return MessageDigest.isEqual(expectHex.getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
            }
            expectHex = hmacHex(secret, signedPayload);
            return MessageDigest.isEqual(expectHex.getBytes(StandardCharsets.UTF_8), header.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private static String hmacHex(String secret, String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    // ==================== 内部 ====================

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static JsonNode call(ChannelUnipileProperties cfg, String apiKey, String method, String path, JsonNode body) {
        if (StrUtil.isBlank(cfg.baseUrl())) {
            throw new UnipileException("未配置 Unipile 实例地址（cgch.unipile.dsn）");
        }
        if (StrUtil.isBlank(apiKey)) {
            throw new UnipileException("缺少 Unipile API Key");
        }
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("X-API-KEY", apiKey)
                    .header("accept", "application/json");
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("content-type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            String text = resp.body() == null ? "" : resp.body();
            if (resp.statusCode() >= 300) {
                throw new UnipileException("Unipile " + method + " " + path + " 失败（HTTP " + resp.statusCode() + "）："
                        + StrUtil.maxLength(text, 300));
            }
            return text.isEmpty() ? JSON.createObjectNode() : JSON.readTree(text);
        } catch (UnipileException e) {
            throw e;
        } catch (Exception e) {
            throw new UnipileException("调用 Unipile 失败：" + e.getMessage());
        }
    }
}
