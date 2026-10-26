package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.module.channel.service.social.ChannelSocialService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 评论渠道（YouTube、TikTok）：定时拉取视频下的新评论进「会话」。间隔由 cgch.social.poll-ms 控制（默认 3 分钟）。
 */
@Component
@Slf4j
public class SocialCommentScheduler {

    @Resource
    private ChannelSocialService channelSocialService;

    @Scheduled(initialDelayString = "${cgch.social.poll-ms:180000}", fixedDelayString = "${cgch.social.poll-ms:180000}")
    public void run() {
        int n = channelSocialService.pollAll();
        if (n > 0) {
            log.info("[run][拉取到 {} 条新评论]", n);
        }
    }

}
