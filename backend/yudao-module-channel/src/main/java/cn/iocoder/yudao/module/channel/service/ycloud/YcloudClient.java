package cn.iocoder.yudao.module.channel.service.ycloud;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient.WaApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
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
import java.util.List;
import java.util.UUID;

/**
 * YCloud 接口（https://docs.ycloud.com）：创建扫码绑定链接（Partner Direct Link）、查号码、发消息、上传 / 下载媒体、查模板，
 * 以及校验 Webhook 签名。请求头 X-API-Key 鉴权。
 */
public final class YcloudClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private YcloudClient() {
    }

    private static String url(ChannelOAuthProperties.Ycloud c, String path) {
        return StrUtil.removeSuffix(StrUtil.blankToDefault(c.getApiBase(), "https://api.ycloud.com/v2"), "/") + path;
    }

    private static JsonNode call(ChannelOAuthProperties.Ycloud c, String method, String path, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(c, path))).timeout(Duration.ofSeconds(30))
                    .header("X-API-Key", c.getApiKey()).header("Accept", "application/json");
            if (body == null) {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            }
            return read(HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString()));
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("无法连接 YCloud：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private static JsonNode read(HttpResponse<String> resp) throws Exception {
        JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
        if (resp.statusCode() >= 300) {
            JsonNode err = json.path("error");
            String msg = StrUtil.blankToDefault(err.path("message").asText(null),
                    StrUtil.blankToDefault(json.path("message").asText(null), "HTTP " + resp.statusCode()));
            JsonNode wa = json.path("whatsappApiError");
            if (wa.has("message")) {
                msg += "（" + wa.path("message").asText() + "）";
            }
            throw new WaApiException("YCloud：" + msg);
        }
        return json;
    }

    // ==================== 扫码绑定 ====================

    public record OnboardingLink(String url, long expiresAt) {
    }

    /**
     * 创建客户绑定 WhatsApp 的链接（有效 2 小时，绑定一个 WABA）。
     *
     * @param coexist true：共存模式（手机上正在用的 WhatsApp Business App 号码，扫码后两边同时使用）
     */
    public static OnboardingLink createOnboardingLink(ChannelOAuthProperties.Ycloud c, String partnerCustomerId, boolean coexist) {
        ObjectNode body = JSON.createObjectNode().put("partnerCustomerId", partnerCustomerId)
                .put("onboardingType", coexist ? "WHATSAPP_BUSINESS_APP" : "WHATSAPP_BUSINESS_PLATFORM")
                .put("locale", "zh_CN");
        JsonNode r = call(c, "POST", "/partner/embeddedSignup/links", body);
        String u = r.path("onboardingUrl").asText(null);
        if (StrUtil.isBlank(u)) {
            throw new WaApiException("YCloud 没有返回绑定链接，请确认已开通 Partner Direct Link");
        }
        return new OnboardingLink(u, r.path("expiresAt").asLong(0));
    }

    // ==================== 号码 ====================

    public record PhoneNumber(String id, String phoneNumber, String displayPhoneNumber, String verifiedName, String wabaId) {
    }

    public static List<PhoneNumber> listPhoneNumbers(ChannelOAuthProperties.Ycloud c, String wabaId) {
        JsonNode r = call(c, "GET", "/whatsapp/phoneNumbers?limit=100&filter.wabaId="
                + URLEncoder.encode(StrUtil.nullToEmpty(wabaId), StandardCharsets.UTF_8), null);
        java.util.ArrayList<PhoneNumber> out = new java.util.ArrayList<>();
        for (JsonNode p : r.path("items")) {
            out.add(new PhoneNumber(p.path("id").asText(null), p.path("phoneNumber").asText(null),
                    p.path("displayPhoneNumber").asText(null), p.path("verifiedName").asText(null), p.path("wabaId").asText(wabaId)));
        }
        return out;
    }

    // ==================== 发消息 ====================

    /** 同步发送（sendDirectly），返回 wamid（没有时返回 YCloud 消息编号） */
    private static String send(ChannelOAuthProperties.Ycloud c, ObjectNode body) {
        JsonNode r = call(c, "POST", "/whatsapp/messages/sendDirectly", body);
        if ("failed".equals(r.path("status").asText())) {
            throw new WaApiException("发送失败：" + StrUtil.blankToDefault(r.path("errorMessage").asText(null),
                    StrUtil.blankToDefault(r.path("whatsappApiError").path("message").asText(null), r.path("errorCode").asText("未知原因"))));
        }
        return StrUtil.blankToDefault(r.path("wamid").asText(null), r.path("id").asText(null));
    }

    private static ObjectNode base(String from, String to, String type) {
        return JSON.createObjectNode().put("from", e164(from)).put("to", e164(to)).put("type", type);
    }

    public static String sendText(ChannelOAuthProperties.Ycloud c, String from, String to, String text) {
        ObjectNode b = base(from, to, "text");
        b.putObject("text").put("body", text);
        return send(c, b);
    }

    public static String sendTemplate(ChannelOAuthProperties.Ycloud c, String from, String to, String name, String language,
                                      List<String> params) {
        ObjectNode b = base(from, to, "template");
        ObjectNode t = b.putObject("template").put("name", name);
        t.putObject("language").put("code", StrUtil.blankToDefault(language, "en_US"));
        if (params != null && !params.isEmpty()) {
            ArrayNode ps = t.putArray("components").addObject().put("type", "body").putArray("parameters");
            params.forEach(p -> ps.addObject().put("type", "text").put("text", StrUtil.nullToEmpty(p)));
        }
        return send(c, b);
    }

    /** @param kind image / video / audio / document */
    public static String sendMedia(ChannelOAuthProperties.Ycloud c, String from, String to, String kind, String mediaId,
                                   String caption, String fileName) {
        ObjectNode b = base(from, to, kind);
        ObjectNode m = b.putObject(kind).put("id", mediaId);
        if (StrUtil.isNotBlank(caption) && !"audio".equals(kind)) {
            m.put("caption", caption);
        }
        if ("document".equals(kind) && StrUtil.isNotBlank(fileName)) {
            m.put("filename", fileName);
        }
        return send(c, b);
    }

    /** 上传媒体（multipart/form-data），返回媒体编号 */
    public static String upload(ChannelOAuthProperties.Ycloud c, String from, byte[] content, String fileName, String mimeType) {
        String boundary = "----cgch" + UUID.randomUUID().toString().replace("-", "");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                    + fileName.replace("\"", "") + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url(c, "/whatsapp/media/"
                            + URLEncoder.encode(e164(from), StandardCharsets.UTF_8) + "/upload")))
                    .timeout(Duration.ofSeconds(120)).header("X-API-Key", c.getApiKey())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build();
            JsonNode r = read(HTTP.send(req, HttpResponse.BodyHandlers.ofString()));
            String id = r.path("id").asText(null);
            if (StrUtil.isBlank(id)) {
                throw new WaApiException("YCloud 没有返回媒体编号");
            }
            return id;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("上传失败：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 下载收到的媒体（推送里的 link，30 天内带 X-API-Key 可下载） */
    public static byte[] download(ChannelOAuthProperties.Ycloud c, String link, int maxBytes) {
        try {
            HttpResponse<byte[]> resp = HTTP.send(HttpRequest.newBuilder(URI.create(link)).timeout(Duration.ofSeconds(60))
                    .header("X-API-Key", c.getApiKey()).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() >= 300) {
                throw new WaApiException("下载媒体失败：HTTP " + resp.statusCode());
            }
            if (resp.body().length > maxBytes) {
                throw new WaApiException("媒体文件过大");
            }
            return resp.body();
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("下载媒体失败：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 模板列表，转换成与 Meta Graph API 相同的结构（data[]），模板页面可以直接用 */
    public static JsonNode listTemplates(ChannelOAuthProperties.Ycloud c, String wabaId) {
        JsonNode r = call(c, "GET", "/whatsapp/templates?limit=100&filter.wabaId="
                + URLEncoder.encode(StrUtil.nullToEmpty(wabaId), StandardCharsets.UTF_8), null);
        ObjectNode out = JSON.createObjectNode();
        out.set("data", r.path("items").isArray() ? r.path("items") : JSON.createArrayNode());
        return out;
    }

    // ==================== Webhook ====================

    /** 校验 YCloud-Signature: t=时间戳,s=HMAC-SHA256(secret, t + "." + 原始请求体)；时间戳与当前相差超过 5 分钟视为无效 */
    public static boolean verifySignature(String secret, byte[] body, String header) {
        if (StrUtil.hasBlank(secret, header) || body == null) {
            return false;
        }
        String ts = null;
        List<String> sigs = new java.util.ArrayList<>();
        for (String part : header.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if ("t".equals(kv[0])) {
                if (ts != null) {
                    return false;
                }
                ts = kv[1];
            } else if ("s".equals(kv[0])) {
                sigs.add(kv[1]);
            }
        }
        if (ts == null || !ts.matches("[0-9]{1,12}") || Math.abs(Instant.now().getEpochSecond() - Long.parseLong(ts)) > 300) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((ts + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(body);
            for (String s : sigs) {
                if (s.matches("[a-fA-F0-9]{64}") && MessageDigest.isEqual(expected, HexFormat.of().parseHex(s))) {
                    return true;
                }
            }
            return false;
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

    /** 号码统一成 E.164（+ 开头） */
    public static String e164(String phone) {
        String d = StrUtil.nullToEmpty(phone).replaceAll("[^0-9]", "");
        return d.isEmpty() ? "" : "+" + d;
    }

}
