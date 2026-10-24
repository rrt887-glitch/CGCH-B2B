package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 联网搜索（Tavily 兼容接口：POST {base}/search，返回 results[].title / url / content）。
 * 用于「预估报价 → 允许联网报价」：企业资料里没有价格时，检索公开市场参考价给 AI 参考。
 */
@Slf4j
public final class WebSearchClient {

    public static final String DEFAULT_BASE_URL = "https://api.tavily.com";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    /** 客户在问价格 */
    private static final Pattern ASKS_PRICE = Pattern.compile(
            "(?i)\\b(price|prices|pricing|quote|quotation|cost|how much|fob|cif|exw|unit price|rate)\\b|报价|价格|多少钱|单价|precio|prix|preis|preço|цена|سعر");
    /** 资料里已经有价格（货币符号或货币代码 + 数字） */
    private static final Pattern HAS_PRICE = Pattern.compile(
            "(?i)(US\\$|USD|\\$|€|EUR|¥|RMB|CNY|£|GBP)\\s?\\d|\\d+(\\.\\d+)?\\s?(USD|EUR|RMB|CNY|元|美元|美金)");

    private WebSearchClient() {
    }

    public record Hit(String title, String url, String content) {
    }

    public static boolean asksPrice(String text) {
        return text != null && ASKS_PRICE.matcher(text).find();
    }

    public static boolean hasPrice(String text) {
        return text != null && HAS_PRICE.matcher(text).find();
    }

    public static List<Hit> search(String baseUrl, String apiKey, String query) {
        String url = StrUtil.removeSuffix(StrUtil.blankToDefault(StrUtil.trim(baseUrl), DEFAULT_BASE_URL), "/") + "/search";
        try {
            String body = JSON.writeValueAsString(Map.of("api_key", apiKey, "query", StrUtil.maxLength(query, 380),
                    "max_results", 5, "search_depth", "basic"));
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("[search][联网搜索失败 {}] {}", resp.statusCode(), StrUtil.maxLength(resp.body(), 300));
                return List.of();
            }
            List<Hit> out = new ArrayList<>();
            for (JsonNode r : JSON.readTree(resp.body()).path("results")) {
                String content = StrUtil.trim(r.path("content").asText(""));
                if (!content.isEmpty()) {
                    out.add(new Hit(StrUtil.maxLength(r.path("title").asText(""), 200), r.path("url").asText(""),
                            StrUtil.maxLength(content, 600)));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[search][联网搜索异常] {}", e.getMessage());
            return List.of();
        }
    }

}
