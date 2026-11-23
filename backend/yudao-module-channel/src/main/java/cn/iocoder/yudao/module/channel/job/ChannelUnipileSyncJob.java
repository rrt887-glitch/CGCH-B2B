package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.module.channel.service.unipile.ChannelUnipileService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Unipile 消息补偿同步：webhook 是主力，但服务器短暂宕机 / 推送丢失时靠这里兜底。
 *
 * 每 5 分钟跑一次，只拉「最近有更新」的会话，按消息编号去重，所以重复执行不会产生重复消息。
 */
@Component
@Slf4j
public class ChannelUnipileSyncJob {

    @Resource
    private ChannelUnipileService channelUnipileService;

    @Scheduled(initialDelayString = "${cgch.unipile.sync-initial-ms:90000}",
            fixedDelayString = "${cgch.unipile.sync-interval-ms:300000}")
    public void sync() {
        if (!channelUnipileService.enabled()) {
            return;
        }
        try {
            channelUnipileService.syncRecentMessages();
        } catch (Exception e) {
            log.warn("[sync][Unipile 补偿同步失败] {}", e.getMessage());
        }
    }
}
