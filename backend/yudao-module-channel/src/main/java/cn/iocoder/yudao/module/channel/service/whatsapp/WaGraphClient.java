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
 * Meta Graph API 的最小封装：WhatsApp Cloud API（发消息、查模板、查号码信息），
 * Messenger 平台（Facebook 主页私信、Instagram 私信）的收发，以及 Webhook 签名校验。
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

    static JsonNode call(ChannelWaAccountDO a, String method, String path, JsonNode body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url(a, path)))
                    .timeout(Duration.ofSeconds(30));
            if (StrUtil.isNotBlank(a.getAccessToken())) {
                b.header("Authorization", "Bearer " + a.getAccessToken());
            }
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

    // ==================== 媒体 ====================

    /** 媒体文件信息：下载地址（约 5 分钟有效）与类型 */
    public record MediaInfo(String url, String mimeType) {
    }

    public static MediaInfo getMedia(ChannelWaAccountDO a, String mediaId) {
        JsonNode json = call(a, "GET", mediaId, null);
        String url = json.path("url").asText(null);
        if (StrUtil.isBlank(url)) {
            throw new WaApiException("媒体文件不存在或已过期（Meta 只保留 30 天）");
        }
        return new MediaInfo(url, json.path("mime_type").asText(null));
    }

    /** 下载媒体内容；地址需要带同一个访问令牌 */
    public static byte[] download(ChannelWaAccountDO a, String url, int maxBytes) {
        try {
            HttpResponse<byte[]> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + a.getAccessToken()).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
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

    /** 上传媒体到 Meta，返回媒体编号（multipart/form-data） */
    public static String upload(ChannelWaAccountDO a, byte[] content, String fileName, String mimeType) {
        String boundary = "----cgch" + System.nanoTime();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            String safeName = StrUtil.blankToDefault(fileName, "file").replace("\"", "");
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"messaging_product\"\r\n\r\nwhatsapp\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"type\"\r\n\r\n" + mimeType + "\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + safeName
                    + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url(a, a.getPhoneNumberId() + "/media")))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + a.getAccessToken())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300 || json.has("error")) {
                throw new WaApiException(json.path("error").path("message").asText("上传失败：HTTP " + resp.statusCode()));
            }
            String id = json.path("id").asText(null);
            if (StrUtil.isBlank(id)) {
                throw new WaApiException("Graph API 未返回媒体编号");
            }
            return id;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("上传媒体失败：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    /**
     * 发送媒体消息，返回 wamid。
     *
     * @param kind image、video、audio、document
     */
    public static String sendMedia(ChannelWaAccountDO a, String to, String kind, String mediaId, String caption, String fileName) {
        ObjectNode body = JSON.createObjectNode();
        body.put("messaging_product", "whatsapp");
        body.put("recipient_type", "individual");
        body.put("to", to);
        body.put("type", kind);
        ObjectNode media = body.putObject(kind);
        media.put("id", mediaId);
        if (StrUtil.isNotBlank(caption) && !"audio".equals(kind)) {
            media.put("caption", caption);
        }
        if ("document".equals(kind) && StrUtil.isNotBlank(fileName)) {
            media.put("filename", fileName);
        }
        return messageId(call(a, "POST", a.getPhoneNumberId() + "/messages", body));
    }

    /** 按 MIME 类型选择 WhatsApp 的媒体消息类型；不支持预览的一律按文件发送 */
    public static String mediaKind(String mimeType) {
        String m = StrUtil.nullToEmpty(mimeType).toLowerCase();
        if (m.equals("image/jpeg") || m.equals("image/png")) {
            return "image";
        }
        if (m.equals("video/mp4") || m.equals("video/3gpp")) {
            return "video";
        }
        if (m.startsWith("audio/")) {
            return "audio";
        }
        return "document";
    }

    // ==================== Messenger / Instagram ====================

    /** 主页或 Instagram 账号的展示名称；同时用于验证令牌是否有效 */
    public static String getMessagingProfile(ChannelWaAccountDO a) {
        boolean ig = ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(a.getPlatform());
        JsonNode json = call(a, "GET", a.getPhoneNumberId() + "?fields=" + (ig ? "id,username,name" : "id,name"), null);
        if (ig) {
            String u = json.path("username").asText(null);
            return StrUtil.isNotBlank(u) ? "@" + u : json.path("name").asText(null);
        }
        return json.path("name").asText(null);
    }

    /** 客户昵称（Messenger 需要 pages_messaging 权限；拿不到时返回 null） */
    public static String getMessagingUserName(ChannelWaAccountDO a, String userId) {
        try {
            if (ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(a.getPlatform())) {
                JsonNode j = call(a, "GET", userId + "?fields=name,username", null);
                return StrUtil.blankToDefault(j.path("name").asText(null), j.path("username").asText(null));
            }
            JsonNode j = call(a, "GET", userId + "?fields=first_name,last_name", null);
            String name = (j.path("first_name").asText("") + " " + j.path("last_name").asText("")).trim();
            return StrUtil.emptyToNull(name);
        } catch (WaApiException e) {
            return null;
        }
    }

    /** 回复文本（客户来信后 24 小时内），返回消息编号 mid */
    public static String sendMessagingText(ChannelWaAccountDO a, String recipientId, String text) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("recipient").put("id", recipientId);
        body.put("messaging_type", "RESPONSE");
        ObjectNode m = body.putObject("message");
        m.put("text", text);
        if (!ChannelWaAccountDO.PLATFORM_INSTAGRAM.equals(a.getPlatform())) {
            m.put("metadata", ECHO_METADATA); // 回显事件里带回，用于识别系统自己发出的消息
        }
        return mid(call(a, "POST", "me/messages", body));
    }

    /** 回显事件中标记系统自己发出的消息 */
    public static final String ECHO_METADATA = "cgch";

    /**
     * 上传并发送附件（Messenger），返回 mid。
     *
     * @param kind image、video、audio、file
     */
    public static String sendMessagingAttachment(ChannelWaAccountDO a, String recipientId, String kind,
                                                 byte[] content, String fileName, String mimeType) {
        ObjectNode recipient = JSON.createObjectNode().put("id", recipientId);
        ObjectNode message = JSON.createObjectNode();
        message.putObject("attachment").put("type", kind).putObject("payload").put("is_reusable", false);
        java.util.LinkedHashMap<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("recipient", recipient.toString());
        fields.put("messaging_type", "RESPONSE");
        fields.put("message", message.toString());
        return mid(multipart(a, "me/messages", fields, "filedata", content, fileName, mimeType));
    }

    /** Messenger 附件类型 */
    public static String messagingKind(String mimeType) {
        String m = StrUtil.nullToEmpty(mimeType).toLowerCase();
        if (m.startsWith("image/")) {
            return "image";
        }
        if (m.startsWith("video/")) {
            return "video";
        }
        if (m.startsWith("audio/")) {
            return "audio";
        }
        return "file";
    }

    /** 订阅主页的消息推送（Facebook 登录授权后调用一次） */
    public static void subscribePage(ChannelWaAccountDO page) {
        call(page, "POST", page.getPhoneNumberId() + "/subscribed_apps?subscribed_fields="
                + URLEncoder.encode("messages,messaging_postbacks,message_echoes,message_deliveries,message_reads", StandardCharsets.UTF_8), null);
    }

    private static String mid(JsonNode json) {
        String id = json.path("message_id").asText(null);
        if (StrUtil.isBlank(id)) {
            throw new WaApiException("Graph API 未返回消息编号");
        }
        return id;
    }

    private static JsonNode multipart(ChannelWaAccountDO a, String path, java.util.Map<String, String> fields,
                                      String fileField, byte[] content, String fileName, String mimeType) {
        String boundary = "----cgch" + System.nanoTime();
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            for (java.util.Map.Entry<String, String> f : fields.entrySet()) {
                out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n\r\n"
                        + f.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
            }
            String safeName = StrUtil.blankToDefault(fileName, "file").replace("\"", "");
            out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + safeName
                    + "\"\r\nContent-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(content);
            out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url(a, path)))
                            .timeout(Duration.ofSeconds(120))
                            .header("Authorization", "Bearer " + a.getAccessToken())
                            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300 || json.has("error")) {
                throw new WaApiException(json.path("error").path("message").asText("上传失败：HTTP " + resp.statusCode()));
            }
            return json;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("上传失败：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
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
