package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.module.channel.service.mailbatch.ChannelMailBatchService;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 邮件群发：定时按每个任务的速率发出下一封。间隔由 cgch.mail-batch.tick-ms 控制（默认 2 秒）。
 */
@Component
public class MailBatchScheduler {

    @Resource
    private ChannelMailBatchService channelMailBatchService;

    @Scheduled(initialDelayString = "${cgch.mail-batch.tick-ms:2000}", fixedDelayString = "${cgch.mail-batch.tick-ms:2000}")
    public void run() {
        channelMailBatchService.tick();
    }

}
