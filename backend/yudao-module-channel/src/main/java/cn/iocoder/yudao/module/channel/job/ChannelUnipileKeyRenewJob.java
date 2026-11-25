package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.module.channel.service.unipile.ChannelUnipileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Unipile 租户 Key 续期：每天凌晨 3:30 检查一次，到期前 30 天自动换新。
 *
 * 不做的后果：Key 到期那天，所有客户的 LinkedIn/Facebook/Instagram/Telegram 同时失效，且只能人工处理。
 */
@Component
@Slf4j
public class ChannelUnipileKeyRenewJob {

    @Resource
    private ChannelUnipileService channelUnipileService;

    @Scheduled(cron = "${cgch.unipile.key-renew-cron:0 30 3 * * ?}")
    public void renew() {
        if (!channelUnipileService.enabled()) {
            return;
        }
        try {
            channelUnipileService.renewExpiringKeys();
        } catch (Exception e) {
            log.warn("[renew][Unipile Key 续期任务失败] {}", e.getMessage());
        }
    }
}
