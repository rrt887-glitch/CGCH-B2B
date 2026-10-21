package cn.iocoder.yudao.module.channel.job;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.channel.dal.dataobject.worktime.ChannelAiDeferredDO;
import cn.iocoder.yudao.module.channel.dal.mysql.worktime.ChannelAiDeferredMapper;
import cn.iocoder.yudao.module.channel.service.ai.ChannelAiService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「业务员优先」到期检查：定时扫描已到期的等待项，无人回复的交给 AI。
 * 扫描间隔由 cgch.ai.deferred-interval-ms 控制（默认 20 秒）。
 */
@Component
@Slf4j
public class AiDeferredScheduler {

    @Resource
    private ChannelAiDeferredMapper channelAiDeferredMapper;
    @Resource
    private ChannelAiService channelAiService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.notify.ChannelNotifyService channelNotifyService;
    @Resource
    private cn.iocoder.yudao.module.channel.service.worktime.ChannelWorkTimeService channelWorkTimeService;

    @Scheduled(initialDelayString = "${cgch.ai.deferred-interval-ms:20000}", fixedDelayString = "${cgch.ai.deferred-interval-ms:20000}")
    public void run() {
        List<ChannelAiDeferredDO> due = TenantUtils.executeIgnore(() -> channelAiDeferredMapper.selectDue(LocalDateTime.now(), 100));
        for (ChannelAiDeferredDO d : due) {
            try {
                TenantUtils.execute(d.getTenantId(), () -> {
                    if (!channelAiDeferredMapper.finish(d.getId(), ChannelAiDeferredDO.STATUS_PROCESSING)) {
                        return; // 已被其他实例领取
                    }
                    String status;
                    try {
                        status = channelAiService.resumeDeferred(d);
                    } catch (Exception e) {
                        log.warn("[run][等待项({}) AI 接手失败] {}", d.getId(), e.getMessage());
                        status = ChannelAiDeferredDO.STATUS_AI_HANDLED;
                    }
                    channelAiDeferredMapper.updateById(new ChannelAiDeferredDO().setId(d.getId()).setStatus(status));
                    if (ChannelAiDeferredDO.STATUS_AI_HANDLED.equals(status)) {
                        Integer m = channelWorkTimeService.getConfig().getTakeoverMinutes();
                        channelNotifyService.onTakeover(d.getChannel(), d.getRefId(), m == null ? 0 : m);
                    }
                });
            } catch (Exception e) {
                log.warn("[run][等待项({}) 处理失败] {}", d.getId(), e.getMessage());
            }
        }
    }

}
