package cn.iocoder.yudao.module.channel.service.whatsapp;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.dal.dataobject.whatsapp.ChannelWaAccountDO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.HexFormat;
import java.util.List;

/**
 * Meta WhatsApp Cloud API（Graph API）的最小封装：发消息、查模板、查号码信息、校验 Webhook 签名。
 */
public final class WaGraphClient {

    public static final String DEFAULT_API_BASE = "https://graph.facebook.com";
    public static final String DEFAULT_API_VERSION = "v24.0";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    private WaGraphClient() {
    }

    /** Graph API 调用失败，message 为 Meta 返回的错误说明 */
    public static class WaApiException extends RuntimeException {
        public WaApiException(String message) {
            super(message);
        }
    }

    private static String url(ChannelWaAccountDO a, String path) {
        String base = StrUtil.removeSuffix(StrUtil.blankToDefault(a.getApiBase(), DEFAULT_API_BASE), "/");
        String version = StrUtil.blankToDefault(a.getApiVersion(), DEFAULT_API_VERSION);
        return base + "/" + version + "/" + path;
    }

    private static JsonNode call(ChannelWaAccountDO a, String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(a, path)))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + a.getAccessToken());
            if (body != null) {
                b.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300 || json.has("error")) {
                JsonNode err = json.path("error");
                String msg = err.path("error_user_msg").asText(err.path("message").asText("HTTP " + resp.statusCode()));
                String code = err.path("code").asText("");
                throw new WaApiException(StrUtil.isBlank(code) ? msg : msg + "（错误码 " + code + "）");
            }
            return json;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("无法连接 Graph API：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /** 查询号码信息，用于测试令牌与 Phone number ID 是否有效；返回展示用号码 */
    public static String getDisplayPhone(ChannelWaAccountDO a) {
        JsonNode json = call(a, "GET", a.getPhoneNumberId()
                + "?fields=display_phone_number,verified_name,quality_rating", null);
        return json.path("display_phone_number").asText(null);
    }

    /** 发送文本消息（仅限 24 小时客服窗口内），返回 wamid */
    public static String sendText(ChannelWaAccountDO a, String to, String text) {
        ObjectNode body = JSON.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", "text");
        ObjectNode t = body.putObject("text");
        t.put("preview_url", true);
        t.put("body", text);
        return messageId(call(a, "POST", a.getPhoneNumberId() + "/messages", body));
    }

    /**
     * 发送模板消息（窗口外首次触达必须用已审核的模板），返回 wamid。
     *
     * @param bodyParams 模板正文中 {{1}}、{{2}}… 的取值，按顺序
     */
    public static String sendTemplate(ChannelWaAccountDO a, String to, String name, String language,
                                      List<String> bodyParams) {
        ObjectNode body = JSON.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("to", to);
        body.put("type", "template");
        ObjectNode tpl = body.putObject("template");
        tpl.put("name", name);
        tpl.putObject("language").put("code", language);
        if (bodyParams != null && !bodyParams.isEmpty()) {
            ArrayNode params = tpl.putArray("components").addObject().put("type", "body").putArray("parameters");
            bodyParams.forEach(p -> params.addObject().put("type", "text").put("text", p));
        }
        return messageId(call(a, "POST", a.getPhoneNumberId() + "/messages", body));
    }

    /** 已提交到 Meta 的消息模板（含审核状态） */
    public static JsonNode listTemplates(ChannelWaAccountDO a) {
        String fields = URLEncoder.encode("name,language,status,category,components", StandardCharsets.UTF_8);
        return call(a, "GET", a.getWabaId() + "/message_templates?limit=200&fields=" + fields, null).path("data");
    }

    private static String messageId(JsonNode json) {
        String id = json.path("messages").path(0).path("id").asText(null);
        if (StrUtil.isBlank(id)) {
            throw new WaApiException("Graph API 未返回消息编号");
        }
        return id;
    }

    /** 校验 X-Hub-Signature-256：sha256=HMAC_SHA256(appSecret, 原始请求体) */
    public static boolean verifySignature(String appSecret, byte[] rawBody, String header) {
        if (StrUtil.isBlank(appSecret) || StrUtil.isBlank(header) || !header.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] given = HexFormat.of().parseHex(header.substring("sha256=".length()).trim());
            return MessageDigest.isEqual(expected, given);
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

}
