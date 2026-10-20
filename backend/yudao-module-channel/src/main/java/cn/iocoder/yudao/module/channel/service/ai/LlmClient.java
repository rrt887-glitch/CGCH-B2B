package cn.iocoder.yudao.module.channel.service.ai;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容的 Chat Completions 客户端。DeepSeek、通义千问（兼容模式）、Kimi、智谱、OpenAI 等都支持这种调用方式。
 */
public final class LlmClient {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    private LlmClient() {
    }

    public record Message(String role, String content) {
        public static Message system(String c) {
            return new Message("system", c);
        }

        public static Message user(String c) {
            return new Message("user", c);
        }

        public static Message assistant(String c) {
            return new Message("assistant", c);
        }
    }

    public record Result(String content, int promptTokens, int completionTokens) {
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String message) {
            super(message);
        }
    }

    public record Embeddings(List<float[]> vectors, int tokens) {
    }

    /** OpenAI 兼容的 /embeddings，按输入顺序返回向量 */
    public static Embeddings embed(String baseUrl, String apiKey, String model, List<String> inputs) {
        if (StrUtil.hasBlank(baseUrl, apiKey, model)) {
            throw new LlmException("请先填写大模型接口地址、API Key 和向量模型名称");
        }
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", model);
            ArrayNode arr = body.putArray("input");
            inputs.forEach(arr::add);
            String url = StrUtil.removeSuffix(baseUrl.trim(), "/") + "/embeddings";
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(60))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300 || json.has("error")) {
                JsonNode err = json.path("error");
                String msg = err.isTextual() ? err.asText() : err.path("message").asText("HTTP " + resp.statusCode());
                throw new LlmException("向量模型调用失败：" + msg);
            }
            float[][] out = new float[inputs.size()][];
            int i = 0;
            for (JsonNode d : json.path("data")) {
                int idx = d.has("index") ? d.path("index").asInt() : i;
                JsonNode e = d.path("embedding");
                float[] v = new float[e.size()];
                for (int j = 0; j < v.length; j++) {
                    v[j] = (float) e.get(j).asDouble();
                }
                if (idx >= 0 && idx < out.length) {
                    out[idx] = v;
                }
                i++;
            }
            for (float[] v : out) {
                if (v == null || v.length == 0) {
                    throw new LlmException("向量模型返回的数据不完整");
                }
            }
            JsonNode usage = json.path("usage");
            return new Embeddings(java.util.Arrays.asList(out), usage.path("total_tokens").asInt(usage.path("prompt_tokens").asInt(0)));
        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("请求被中断");
        } catch (Exception e) {
            throw new LlmException("无法连接向量模型接口：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

    public static Result chat(String baseUrl, String apiKey, String model, List<Message> messages, double temperature) {
        if (StrUtil.hasBlank(baseUrl, apiKey, model)) {
            throw new LlmException("请先在数字员工设置中填写大模型接口地址、API Key 和模型名称");
        }
        try {
            ObjectNode body = JSON.createObjectNode();
            body.put("model", model);
            body.put("temperature", temperature);
            body.put("stream", false);
            ArrayNode arr = body.putArray("messages");
            messages.forEach(m -> arr.addObject().put("role", m.role()).put("content", m.content()));
            String url = StrUtil.removeSuffix(baseUrl.trim(), "/") + "/chat/completions";
            HttpResponse<String> resp = HTTP.send(HttpRequest.newBuilder(URI.create(url))
                            .timeout(Duration.ofSeconds(90))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode json = StrUtil.isBlank(resp.body()) ? JSON.createObjectNode() : JSON.readTree(resp.body());
            if (resp.statusCode() >= 300 || json.has("error")) {
                JsonNode err = json.path("error");
                String msg = err.isTextual() ? err.asText() : err.path("message").asText("HTTP " + resp.statusCode());
                throw new LlmException("大模型调用失败：" + msg);
            }
            String content = json.path("choices").path(0).path("message").path("content").asText("");
            if (StrUtil.isBlank(content)) {
                throw new LlmException("大模型没有返回内容");
            }
            JsonNode usage = json.path("usage");
            return new Result(content.trim(), usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0));
        } catch (LlmException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("请求被中断");
        } catch (Exception e) {
            throw new LlmException("无法连接大模型接口：" + StrUtil.blankToDefault(e.getMessage(), e.getClass().getSimpleName()));
        }
    }

}
