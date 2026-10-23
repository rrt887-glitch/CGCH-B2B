package cn.iocoder.yudao.module.channel.service.translate;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.channel.dal.dataobject.ai.ChannelAiConfigDO;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import cn.iocoder.yudao.module.channel.service.ai.LlmClient;
import cn.iocoder.yudao.module.channel.service.credit.ChannelCreditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_CREDIT_INSUFFICIENT;
import static cn.iocoder.yudao.module.channel.enums.ErrorCodeConstants.AI_LLM_FAIL;

/**
 * 翻译（设置 → 翻译设置）：聊天消息译成中文；输入框双语模式下把业务员输入的中文译成客户使用的语言。
 * 用数字员工配置的大模型翻译，同一段文字结果会缓存，按 token 扣智点。
 */
@Service
@Slf4j
public class ChannelTranslateService {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern ARRAY = Pattern.compile("\\[[\\s\\S]*]");
    private static final int MAX_TEXTS = 30;
    private static final int MAX_CHARS = 2000;
    private static final int CACHE_MAX = 20_000;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private ChannelCreditService channelCreditService;

    /**
     * @param target 目标语言，例如「中文」「English」；为空时按 sample 判断
     * @param sample 客户发来的一段文字，译文使用与它相同的语言（输入框双语模式）
     */
    public List<String> translate(Long userId, List<String> texts, String target, String sample) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> src = texts.stream().limit(MAX_TEXTS).map(t -> StrUtil.maxLength(StrUtil.nullToEmpty(t), MAX_CHARS)).toList();
        String lang = StrUtil.isNotBlank(target) ? target.trim()
                : StrUtil.isNotBlank(sample) ? "与这段文字相同的语言：「" + StrUtil.maxLength(sample.trim(), 200) + "」" : "中文";
        String prefix = TenantContextHolder.getTenantId() + "|" + lang + "|";
        String[] out = new String[src.size()];
        List<Integer> todo = new ArrayList<>();
        for (int i = 0; i < src.size(); i++) {
            String s = src.get(i);
            if (StrUtil.isBlank(s)) {
                out[i] = "";
                continue;
            }
            String hit = cache.get(prefix + s);
            if (hit != null) {
                out[i] = hit;
            } else {
                todo.add(i);
            }
        }
        if (!todo.isEmpty()) {
            ChannelAiConfigDO cfg = channelAiService.getConfig();
            if (StrUtil.hasBlank(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel())) {
                throw exception(AI_LLM_FAIL, "请先在「设置 → AI 接待设置」中配置大模型");
            }
            if (!channelCreditService.hasBalance()) {
                throw exception(AI_CREDIT_INSUFFICIENT);
            }
            List<String> batch = todo.stream().map(src::get).toList();
            String system = "你是专业的外贸翻译。把用户给出的 JSON 字符串数组中的每一项翻译成" + lang
                    + "。保持原意、语气和格式（换行、数字、型号、链接不变），已经是目标语言的原样返回。"
                    + "只输出一个同样长度的 JSON 字符串数组，不要输出其他内容。";
            try {
                LlmClient.Result r = LlmClient.chat(cfg.getLlmBaseUrl(), cfg.getLlmApiKey(), cfg.getLlmModel(),
                        List.of(LlmClient.Message.system(system), LlmClient.Message.user(JSON.writeValueAsString(batch))), 0);
                channelCreditService.consume("translate", 0L, userId, r.promptTokens() + r.completionTokens(), "翻译");
                Matcher m = ARRAY.matcher(r.content());
                JsonNode arr = m.find() ? JSON.readTree(m.group()) : null;
                for (int k = 0; k < todo.size(); k++) {
                    String t = arr != null && arr.isArray() && k < arr.size() ? arr.get(k).asText("") : "";
                    if (StrUtil.isBlank(t) && batch.size() == 1 && arr == null) {
                        t = r.content().trim(); // 模型没按数组返回：只有一项时直接用
                    }
                    out[todo.get(k)] = t;
                    if (StrUtil.isNotBlank(t)) {
                        if (cache.size() > CACHE_MAX) {
                            cache.clear();
                        }
                        cache.put(prefix + batch.get(k), t);
                    }
                }
            } catch (LlmClient.LlmException e) {
                throw exception(AI_LLM_FAIL, e.getMessage());
            } catch (Exception e) {
                log.warn("[translate][翻译失败] {}", e.getMessage());
                throw exception(AI_LLM_FAIL, "翻译失败，请稍后再试");
            }
        }
        return Arrays.asList(out);
    }

}
