package cn.iocoder.yudao.module.channel.service.credit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 调用用量记录（已移除「智点」计费）。
 *
 * 背景：本产品的 AI 由客户自己配置大模型 API Key，费用直接结算在客户的模型账户上，
 * 平台不做代付，所以余额、充值、扣点这套逻辑全部移除。这里只保留原调用方需要的两个方法：
 * - {@link #hasBalance()}：永远返回 true（不再有余额限制）
 * - {@link #consume(String, Long, Long, int, String)}：不再计费，只返回 0
 *
 * 如果以后要做「平台代付 + 按 token 计费」，再在这里重新引入用量统计与扣费。
 */
@Service
@Slf4j
public class ChannelAiUsageService {

    /** 不再有余额概念：AI 是否可用只取决于「有没有配置大模型」 */
    public boolean hasBalance() {
        return true;
    }

    /**
     * 记录一次 AI 调用（当前不计费、不入库）。
     *
     * @return 永远返回 0（原「AI 用量数」已废弃）
     */
    public long consume(String channel, Long refId, Long userId, int tokens, String remark) {
        log.debug("[consume][AI 调用][channel={} refId={} userId={} tokens={} remark={}]", channel, refId, userId, tokens, remark);
        return 0L;
    }
}
