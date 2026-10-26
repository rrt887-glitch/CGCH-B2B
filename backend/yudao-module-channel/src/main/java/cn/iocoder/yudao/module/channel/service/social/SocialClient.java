package cn.iocoder.yudao.module.channel.service.social;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.channel.framework.oauth.ChannelOAuthProperties;
import cn.iocoder.yudao.module.channel.service.whatsapp.WaGraphClient.WaApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 评论渠道的官方接口：YouTube Data API v3（commentThreads / comments）与 TikTok API for Business 的 Accounts API
 * （/business/video/list/、/business/comment/list/、/business/comment/reply/create/）。
 */
public final class SocialClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private SocialClient() {
    }

    public record Token(String access, String refresh, long expiresIn, String openId) {
    }

    /** 一条评论（顶层评论或回复） */
    public record Comment(String id, String parentId, String videoId, String videoTitle, String author, String authorId,
                          boolean own, String text, long time) {
    }

    private static String enc(String s) {
        return URLEncoder.encode(StrUtil.nullToEmpty(s), StandardCharsets.UTF_8);
    }

    private static JsonNode send(HttpRequest req) {
        try {
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300) {
                JsonNode err = json.path("error");
                String msg = err.isTextual() ? StrUtil.blankToDefault(json.path("error_description").asText(null), err.asText())
                        : StrUtil.blankToDefault(err.path("message").asText(null), "HTTP " + resp.statusCode());
                throw new WaApiException(msg);
            }
            if (json.has("code") && json.path("code").isNumber() && json.path("code").asInt() != 0) { // TikTok
                throw new WaApiException("TikTok：" + json.path("message").asText("未知错误") + "（" + json.path("code").asInt() + "）");
            }
            return json;
        } catch (WaApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WaApiException("请求被中断");
        } catch (Exception e) {
            throw new WaApiException("请求失败：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    private static JsonNode get(String url, String header, String value) {
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header(header, value).GET().build());
    }

    private static JsonNode postJson(String url, String header, String value, Object body) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            if (header != null) {
                b.header(header, value);
            }
            return send(b.build());
        } catch (WaApiException e) {
            throw e;
        } catch (Exception e) {
            throw new WaApiException("请求失败：" + e.getMessage());
        }
    }

    private static JsonNode postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue())).collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    // ==================== YouTube ====================

    public static String youtubeAuthorizeUrl(ChannelOAuthProperties.Social c, String redirectUri, String state) {
        return c.getAuthUrl() + (c.getAuthUrl().contains("?") ? "&" : "?") + "client_id=" + enc(c.getClientId())
                + "&redirect_uri=" + enc(redirectUri) + "&response_type=code&scope=" + enc(c.getScope())
                + "&access_type=offline&prompt=consent&include_granted_scopes=true&state=" + enc(state);
    }

    public static Token youtubeExchange(ChannelOAuthProperties.Social c, String code, String redirectUri) {
        JsonNode t = postForm(c.getTokenUrl(), Map.of("grant_type", "authorization_code", "code", StrUtil.nullToEmpty(code),
                "redirect_uri", redirectUri, "client_id", c.getClientId(), "client_secret", c.getClientSecret()));
        return new Token(t.path("access_token").asText(""), t.path("refresh_token").asText(""), t.path("expires_in").asLong(3600), null);
    }

    public static Token youtubeRefresh(ChannelOAuthProperties.Social c, String refresh) {
        JsonNode t = postForm(c.getTokenUrl(), Map.of("grant_type", "refresh_token", "refresh_token", refresh,
                "client_id", c.getClientId(), "client_secret", c.getClientSecret()));
        return new Token(t.path("access_token").asText(""), refresh, t.path("expires_in").asLong(3600), null);
    }

    /** 授权账号自己的频道：编号与名称 */
    public static String[] youtubeMyChannel(ChannelOAuthProperties.Social c, String access) {
        JsonNode r = get(c.getApiBase() + "/channels?part=snippet&mine=true", "Authorization", "Bearer " + access);
        JsonNode ch = r.path("items").path(0);
        if (ch.isMissingNode()) {
            throw new WaApiException("这个 Google 账号下没有 YouTube 频道");
        }
        return new String[]{ch.path("id").asText(), ch.path("snippet").path("title").asText(ch.path("id").asText())};
    }

    /** 频道下所有视频的最新评论（按时间倒序，最多 50 个评论串，每串带最近几条回复） */
    public static List<Comment> youtubeComments(ChannelOAuthProperties.Social c, String access, String channelId) {
        JsonNode r = get(c.getApiBase() + "/commentThreads?part=snippet,replies&order=time&maxResults=50&textFormat=plainText"
                + "&allThreadsRelatedToChannelId=" + enc(channelId), "Authorization", "Bearer " + access);
        List<Comment> out = new ArrayList<>();
        Set<String> videoIds = new LinkedHashSet<>();
        for (JsonNode t : r.path("items")) {
            JsonNode top = t.path("snippet").path("topLevelComment");
            String threadId = t.path("id").asText();
            String videoId = t.path("snippet").path("videoId").asText("");
            videoIds.add(videoId);
            out.add(youtubeComment(top, threadId, null, videoId, channelId));
            for (JsonNode rep : t.path("replies").path("comments")) {
                out.add(youtubeComment(rep, threadId, threadId, videoId, channelId));
            }
        }
        Map<String, String> titles = new HashMap<>();
        videoIds.remove("");
        if (!videoIds.isEmpty()) {
            try {
                JsonNode v = get(c.getApiBase() + "/videos?part=snippet&id=" + enc(String.join(",", videoIds)),
                        "Authorization", "Bearer " + access);
                v.path("items").forEach(i -> titles.put(i.path("id").asText(), i.path("snippet").path("title").asText("")));
            } catch (WaApiException ignored) {
                // 标题只是给 AI 的上下文，取不到不影响
            }
        }
        out.replaceAll(x -> new Comment(x.id(), x.parentId(), x.videoId(), titles.getOrDefault(x.videoId(), ""), x.author(),
                x.authorId(), x.own(), x.text(), x.time()));
        return out;
    }

    private static Comment youtubeComment(JsonNode cm, String threadId, String parentId, String videoId, String channelId) {
        JsonNode s = cm.path("snippet");
        String authorId = s.path("authorChannelId").path("value").asText("");
        long time;
        try {
            time = java.time.Instant.parse(s.path("publishedAt").asText()).getEpochSecond();
        } catch (Exception e) {
            time = java.time.Instant.now().getEpochSecond();
        }
        return new Comment(parentId == null ? threadId : cm.path("id").asText(), parentId, videoId, "",
                s.path("authorDisplayName").asText(""), authorId, authorId.equals(channelId),
                StrUtil.blankToDefault(s.path("textOriginal").asText(null), s.path("textDisplay").asText("")), time);
    }

    /** 回复评论串，返回新评论编号 */
    public static String youtubeReply(ChannelOAuthProperties.Social c, String access, String threadId, String text) {
        ObjectNode body = JSON.createObjectNode();
        body.putObject("snippet").put("parentId", threadId).put("textOriginal", text);
        JsonNode r = postJson(c.getApiBase() + "/comments?part=snippet", "Authorization", "Bearer " + access, body);
        return r.path("id").asText("yt_" + UUID.randomUUID());
    }

    // ==================== TikTok ====================

    public static String tiktokAuthorizeUrl(ChannelOAuthProperties.Social c, String state) {
        return c.getAuthUrl() + (c.getAuthUrl().contains("?") ? "&" : "?") + "state=" + enc(state) + "&disable_auto_auth=1";
    }

    public static Token tiktokExchange(ChannelOAuthProperties.Social c, String code, String redirectUri) {
        ObjectNode b = JSON.createObjectNode().put("client_id", c.getClientId()).put("client_secret", c.getClientSecret())
                .put("grant_type", "authorization_code").put("auth_code", StrUtil.nullToEmpty(code)).put("redirect_uri", redirectUri);
        JsonNode d = postJson(c.getApiBase() + "/tt_user/oauth2/token/", null, null, b).path("data");
        return new Token(d.path("access_token").asText(""), d.path("refresh_token").asText(""),
                d.path("expires_in").asLong(86400), d.path("open_id").asText(""));
    }

    public static Token tiktokRefresh(ChannelOAuthProperties.Social c, String refresh) {
        ObjectNode b = JSON.createObjectNode().put("client_id", c.getClientId()).put("client_secret", c.getClientSecret())
                .put("grant_type", "refresh_token").put("refresh_token", refresh);
        JsonNode d = postJson(c.getApiBase() + "/tt_user/oauth2/refresh_token/", null, null, b).path("data");
        return new Token(d.path("access_token").asText(""), StrUtil.blankToDefault(d.path("refresh_token").asText(null), refresh),
                d.path("expires_in").asLong(86400), d.path("open_id").asText(""));
    }

    /** 账号用户名（@ 后面的部分）与显示名称 */
    public static String[] tiktokProfile(ChannelOAuthProperties.Social c, String access, String openId) {
        JsonNode d = get(c.getApiBase() + "/business/get/?business_id=" + enc(openId) + "&fields=" + enc("[\"username\",\"display_name\"]"),
                "Access-Token", access).path("data");
        return new String[]{d.path("username").asText(""), d.path("display_name").asText("")};
    }

    /** 最近 20 个视频下的评论（含每条评论最多 3 条回复） */
    public static List<Comment> tiktokComments(ChannelOAuthProperties.Social c, String access, String openId) {
        JsonNode v = get(c.getApiBase() + "/business/video/list/?business_id=" + enc(openId) + "&max_count=20&fields="
                + enc("[\"item_id\",\"caption\",\"create_time\"]"), "Access-Token", access).path("data");
        JsonNode videos = v.has("videos") ? v.path("videos") : v.path("list");
        List<Comment> out = new ArrayList<>();
        for (JsonNode video : videos) {
            String videoId = video.path("item_id").asText("");
            if (videoId.isEmpty()) {
                continue;
            }
            String title = StrUtil.maxLength(video.path("caption").asText(""), 80);
            JsonNode d = get(c.getApiBase() + "/business/comment/list/?business_id=" + enc(openId) + "&video_id=" + enc(videoId)
                    + "&include_replies=true&sort_field=create_time&sort_order=desc&max_count=30", "Access-Token", access).path("data");
            for (JsonNode cm : d.path("comments")) {
                String id = cm.path("comment_id").asText();
                String parent = cm.path("parent_comment_id").asText(null);
                out.add(tiktokComment(cm, id, parent, videoId, title));
                for (JsonNode rep : cm.path("reply_list")) {
                    out.add(tiktokComment(rep, rep.path("comment_id").asText(), id, videoId, title));
                }
            }
        }
        return out;
    }

    private static Comment tiktokComment(JsonNode cm, String id, String parentId, String videoId, String title) {
        String text = cm.path("text").asText("");
        if (text.isEmpty() && cm.hasNonNull("image_url")) {
            text = "[图片] " + cm.path("image_url").asText();
        }
        return new Comment(id, StrUtil.emptyToNull(parentId), videoId, title,
                StrUtil.blankToDefault(cm.path("display_name").asText(null), cm.path("username").asText("")),
                StrUtil.blankToDefault(cm.path("unique_identifier").asText(null), cm.path("user_id").asText("")),
                cm.path("owner").asBoolean(false), text, cm.path("create_time").asLong(java.time.Instant.now().getEpochSecond()));
    }

    public static String tiktokReply(ChannelOAuthProperties.Social c, String access, String openId, String videoId,
                                     String commentId, String text) {
        ObjectNode b = JSON.createObjectNode().put("business_id", openId).put("video_id", videoId)
                .put("comment_id", commentId).put("text", StrUtil.maxLength(text, 1200));
        JsonNode d = postJson(c.getApiBase() + "/business/comment/reply/create/", "Access-Token", access, b).path("data");
        return d.path("comment_id").asText("tt_" + UUID.randomUUID());
    }

}
