package cn.iocoder.yudao.module.channel.service.evolution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient.WaApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Evolution API v2 接口（https://github.com/evolution-foundation/evolution-api）：建实例、取二维码 / 配对码、查连接状态、
 * 发文本 / 媒体、下载媒体、退出与删除实例。请求头 apikey 为全局 API Key。
 */
public final class EvolutionClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    /** 推送里我们自己的校验头 */
    public static final String TOKEN_HEADER = "x-cgch-token";

    private EvolutionClient() {
    }

    private static JsonNode call(ChannelOAuthProperties.Evolution c, String method, String path, Object body, int timeoutSec) {
        String base = StrUtil.removeSuffix(StrUtil.trim(c.getBaseUrl()), "/");
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(timeoutSec))
                    .header("apikey", c.getApiKey()).header("Accept", "application/json");
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json;
            try {
                json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            } catch (Exception e) {
                json = JSON.createObjectNode().put("message", StrUtil.maxLength(resp.body(), 200));
            }
            if (resp.statusCode() >= 300 || (json.isObject() && json.path("error").asBoolean(false) && json.has("message")
                    && !json.has("instance"))) {
                throw new WaApiException("WhatsApp 扫码服务：" + errorText(json, resp.statusCode()));
            }
            return json;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("无法连接 WhatsApp 扫码服务：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 错误信息形如 {"status":400,"error":"Bad Request","response":{"message":["..."]}} 或 {"error":true,"message":"..."} */
    private static String errorText(JsonNode json, int status) {
        JsonNode m = json.path("response").path("message");
        if (m.isArray() && !m.isEmpty()) {
            JsonNode first = m.path(0);
            return first.isTextual() ? first.asText() : first.toString();
        }
        if (m.isTextual()) {
            return m.asText();
        }
        if (json.path("message").isTextual()) {
            return json.path("message").asText();
        }
        return "HTTP " + status;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ==================== 实例与连接 ====================

    /** 二维码（data:image/png;base64,...）与配对码；连接成功后都为空 */
    public record QrCode(String base64, String pairingCode) {
        static QrCode of(JsonNode n) {
            JsonNode q = n.has("qrcode") ? n.path("qrcode") : n;
            return new QrCode(StrUtil.emptyToNull(q.path("base64").asText(null)), StrUtil.emptyToNull(q.path("pairingCode").asText(null)));
        }
    }

    public record Created(String token, QrCode qr) {
    }

    /**
     * 建实例并开始连接。传号码时返回 8 位配对码（在手机 WhatsApp「关联设备 → 改用电话号码关联」输入），否则返回二维码。
     *
     * @param webhookUrl  推送地址
     * @param webhookToken 推送时带的校验头
     */
    public static Created createInstance(ChannelOAuthProperties.Evolution c, String name, String number,
                                         String webhookUrl, String webhookToken) {
        ObjectNode body = JSON.createObjectNode();
        body.put("instanceName", name);
        body.put("integration", "WHATSAPP-BAILEYS");
        body.put("qrcode", true);
        if (StrUtil.isNotBlank(number)) {
            body.put("number", number);
        }
        body.put("groupsIgnore", true);
        body.put("rejectCall", false);
        body.put("alwaysOnline", false);
        body.put("readMessages", false);
        body.put("readStatus", false);
        body.put("syncFullHistory", false);
        ObjectNode w = body.putObject("webhook");
        w.put("enabled", true);
        w.put("url", webhookUrl);
        w.put("byEvents", false);
        w.put("base64", false);
        w.putObject("headers").put(TOKEN_HEADER, webhookToken);
        ArrayNode ev = w.putArray("events");
        ev.add("CONNECTION_UPDATE").add("MESSAGES_UPSERT").add("MESSAGES_UPDATE");
        JsonNode r = call(c, "POST", "/instance/create", body, 60);
        return new Created(r.path("hash").asText(null), QrCode.of(r));
    }

    /** 连接状态：open、connecting、close；实例不存在返回 null */
    public static String connectionState(ChannelOAuthProperties.Evolution c, String name) {
        try {
            return StrUtil.emptyToNull(call(c, "GET", "/instance/connectionState/" + enc(name), null, 15)
                    .path("instance").path("state").asText(null));
        } catch (WaApiException e) {
            if (StrUtil.containsAnyIgnoreCase(e.getMessage(), "does not exist", "not found", "HTTP 404")) {
                return null;
            }
            throw e;
        }
    }

    /** 连接中时取最新的二维码 / 配对码（二维码约 20 秒刷新一次） */
    public static QrCode currentQr(ChannelOAuthProperties.Evolution c, String name) {
        return QrCode.of(call(c, "GET", "/instance/connect/" + enc(name), null, 30));
    }

    public record Info(String ownerJid, String profileName, String connectionStatus) {
    }

    public static Info fetchInstance(ChannelOAuthProperties.Evolution c, String name) {
        JsonNode r = call(c, "GET", "/instance/fetchInstances?instanceName=" + enc(name), null, 15);
        JsonNode i = r.isArray() ? r.path(0) : r;
        if (i.has("instance")) {
            i = i.path("instance");
        }
        return new Info(StrUtil.emptyToNull(i.path("ownerJid").asText(null)), StrUtil.emptyToNull(i.path("profileName").asText(null)),
                StrUtil.emptyToNull(StrUtil.blankToDefault(i.path("connectionStatus").asText(null), i.path("status").asText(null))));
    }

    /** 删除实例（连接中或已连接时 Evolution 会先退出手机上的关联设备）；实例不存在时忽略 */
    public static void deleteInstance(ChannelOAuthProperties.Evolution c, String name) {
        try {
            call(c, "DELETE", "/instance/delete/" + enc(name), null, 30);
        } catch (WaApiException e) {
            if (!StrUtil.containsAnyIgnoreCase(e.getMessage(), "does not exist", "not found", "HTTP 404")) {
                throw e;
            }
        }
    }

    // ==================== 消息 ====================

    /** 发文本，返回消息编号 */
    public static String sendText(ChannelOAuthProperties.Evolution c, String name, String number, String text) {
        ObjectNode body = JSON.createObjectNode();
        body.put("number", number);
        body.put("text", text);
        body.put("linkPreview", true);
        return messageId(call(c, "POST", "/message/sendText/" + enc(name), body, 60));
    }

    /**
     * 发媒体（内容以 base64 传递），返回消息编号
     *
     * @param kind image、video、audio、document
     */
    public static String sendMedia(ChannelOAuthProperties.Evolution c, String name, String number, String kind,
                                   byte[] content, String mimeType, String fileName, String caption) {
        ObjectNode body = JSON.createObjectNode();
        body.put("number", number);
        body.put("mediatype", kind);
        body.put("mimetype", mimeType);
        body.put("media", Base64.getEncoder().encodeToString(content));
        if (StrUtil.isNotBlank(fileName)) {
            body.put("fileName", fileName);
        }
        if (StrUtil.isNotBlank(caption) && !"audio".equals(kind)) {
            body.put("caption", caption);
        }
        return messageId(call(c, "POST", "/message/sendMedia/" + enc(name), body, 120));
    }

    public record Media(byte[] content, String mimeType, String fileName) {
    }

    /** 下载收到的媒体 */
    public static Media downloadMedia(ChannelOAuthProperties.Evolution c, String name, String messageId) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("message").putObject("key").put("id", messageId);
        body.put("convertToMp4", false);
        JsonNode r = call(c, "POST", "/chat/getBase64FromMediaMessage/" + enc(name), body, 120);
        String b64 = r.path("base64").asText("");
        if (b64.isEmpty()) {
            throw new WaApiException("媒体文件已过期或无法下载");
        }
        return new Media(Base64.getDecoder().decode(b64), StrUtil.emptyToNull(r.path("mimetype").asText(null)),
                StrUtil.emptyToNull(r.path("fileName").asText(null)));
    }

    private static String messageId(JsonNode r) {
        String id = r.path("key").path("id").asText("");
        if (id.isEmpty()) {
            throw new WaApiException("WhatsApp 扫码服务没有返回消息编号");
        }
        return id;
    }

    // ==================== 工具 ====================

    /** jid（8613800001111@s.whatsapp.net）或号码转纯数字 */
    public static String digits(String jidOrPhone) {
        if (jidOrPhone == null) {
            return "";
        }
        String user = jidOrPhone.contains("@") ? jidOrPhone.substring(0, jidOrPhone.indexOf('@')) : jidOrPhone;
        if (user.contains(":")) {
            user = user.substring(0, user.indexOf(':'));
        }
        return user.replaceAll("\\D", "");
    }

    public static JsonNode parse(byte[] raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            return JSON.createObjectNode();
        }
    }

}
